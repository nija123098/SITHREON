package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.*;
import com.nija123098.sithreon.backend.objects.Competitor;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.objects.Team;
import com.nija123098.sithreon.backend.util.*;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.game.management.GameAction;
import com.nija123098.sithreon.game.management.GameArguments;
import com.nija123098.sithreon.game.management.GameRules;
import com.nija123098.sithreon.game.management.GameUpdate;
import org.apache.commons.lang3.tuple.MutablePair;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The {@link Machine} representation of the server responsible
 * for the running of a game through {@link GameClient} instances.
 *
 * @author nija123098
 */
public class GameServer extends Machine {
    private final TransferSocket superServerSocket;
    private final AtomicReference<GameManager> gameManager = new AtomicReference<>();
    private final AtomicReference<Match> match = new AtomicReference<>();
    private final AtomicReference<MutablePair<TransferSocket, TransferSocket>> runnerInstances = new AtomicReference<>();
    private final Map<TransferSocket, List<File>> filesLeftToTransfer = new HashMap<>();
    private final Map<TransferSocket, Integer> originPathLength = new HashMap<>();

    public GameServer() {
        new SocketAcceptor(this, Config.internalPort);
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress, Config.externalPort);
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to establish connection to super server due to IOException", e);
            throw new NoReturnException();
        }
        this.superServerSocket.registerAuthenticationAction((socket) -> socket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER));
    }

    /**
     * Notification to run a game.
     *
     * @param match the {@link Match} to run.
     */
    @Action(MachineAction.RUN_GAME)
    public void runGame(Match match) {
        if (this.match.get() != null) Log.ERROR.log("Unexpected RUN_GAME command for match " + match);
        AtomicBoolean winner = new AtomicBoolean();
        this.gameManager.set(new GameManager(match, team -> {
            if (winner.getAndSet(true)) return;
            this.gameManager.get().gameEnd.set(true);
            Log.INFO.log("Team " + team + " won match " + match);
            this.superServerSocket.write(MachineAction.MATCH_COMPLETE, match, team.getLineup());
            this.match.set(null);// Reset state
            this.runnerInstances.set(null);
            this.gameManager.getAndSet(null).kill();// may want additional warning for complete end.
            Log.INFO.log("Set up for next round, sending READY_TO_SERVE");
            this.superServerSocket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER);
        }));
    }

    @Override
    protected synchronized void notifyReady(ManagedMachineType machineType, TransferSocket socket) {
        if (machineType == ManagedMachineType.GAME_RUNNER) {
            this.gameManager.get().markReady(socket);
        } else super.notifyReady(machineType, socket);
    }

    /**
     * A {@link MachineAction} method for stopping a game due to the HEAD
     * of a {@link Repository} no longer matching the {@link Match}.
     *
     * @param repository the repository out of date.
     */
    @Action(MachineAction.MATCH_OUT_OF_DATE)
    public void outOfDate(Repository repository) {
        this.gameManager.getAndSet(null).kill();
    }

    /**
     * Indicates readiness to relieve the {@link Competitor}'s files.
     *
     * @param competitor the {@link Competitor} for getting the files of.
     * @param socket     the sender.
     */
    @Action(MachineAction.READY_TO_RECEIVE_COMPETITOR_DATA)
    public void readyToReceiveCompetitorData(Competitor competitor, TransferSocket socket) {
        this.gameManager.get().relateConnection(competitor, socket);
        competitor.getRepository().getSource(competitor.getHash());
        List<File> files = new LinkedList<>();
        this.filesLeftToTransfer.put(socket, files);
        this.originPathLength.put(socket, competitor.getRepository().getLocalRepoLocation().length());
        try {
            FileUtil.walk(new File(competitor.getRepository().getLocalRepoLocation()), path -> !path.getName().endsWith(".git"), path -> {
                if (path.isDirectory()) return;
                files.add(path);
            });
        } catch (IOException e) {
            Log.ERROR.log("Unexpected IOException reading data to send for " + competitor, e);
        }
        readyForNextFile(socket);
    }

    @Action(MachineAction.READY_FOR_NEXT_FILE)
    public void readyForNextFile(TransferSocket socket) {
        if (this.filesLeftToTransfer.get(socket).isEmpty()) {
            socket.write(MachineAction.COMPETITOR_DATA_COMPLETE);
            this.filesLeftToTransfer.remove(socket);
            this.originPathLength.remove(socket);
            return;
        }
        File file = this.filesLeftToTransfer.get(socket).remove(0);
        try {
            socket.write(MachineAction.SEND_COMPETITOR_DATA, file.toString().substring(this.originPathLength.get(socket)), Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            Log.ERROR.log("IOException reading file: " + file, e);
        }
    }

    /**
     * Receives a {@link MachineAction#GAME_ACTION} to effect the game.
     *
     * @param action         the {@link GameAction} to effect the game.
     * @param arguments      the wrapping of the arguments to effect the game.
     * @param transferSocket the transfer socket the {@link MachineAction#GAME_ACTION} was received from.
     */
    @Action(MachineAction.GAME_ACTION)
    public void gameAction(GameAction action, GameArguments arguments, TransferSocket transferSocket) {
        if (transferSocket.isClosed()) {
            Log.TRACE.log("Dropped GAME_ACTION of " + action + " closed connection");
            return;
        }
        this.gameManager.get().handle(transferSocket, action, arguments);
    }

    /**
     * Starts a Docker instance for the container with args provided by this instance and the {@link GameRules}.
     *
     * @param competitor the {@link Competitor} to be in the container.
     * @param gameRules  the {@link GameRules} for this {@link Match}.
     * @return the Docker id of the container.
     */
    private ContainerProcess startupRunnerInstance(Competitor competitor, GameRules gameRules) {
        return new ContainerProcess(this, competitor, gameRules, System.out, System.err);// todo add sending to file and requests
    }

    private class ContainerProcess {
        private final String containerName;

        private ContainerProcess(GameServer gameServer, Competitor competitor, GameRules gameRules, OutputStream output, OutputStream error) {
            this.containerName = competitor.getMatchId().replace('+', '-').replace('/', '_').replace('=', '-') + competitor.getTeamNumber();// match ID contains padding (=), but '=' can't be part of a container name.
            List<String> strings = new ArrayList<>(Arrays.asList("docker", "run", "--name", this.containerName, "--rm", "--no-healthcheck", "-e", "COMPETITOR=" + competitor, "-e", "REPOSITORY_URL=" + ConnectionUtil.getExternalProtocolName() + "://" + competitor.getRepository(), "-e", "AUTH_CODE=" + gameServer.superServerSocket.getOneUseAuthenticationCode()));
            strings.addAll(gameRules.getDockerRules(competitor));
            strings.add(competitor.getRepository().getRepositoryConfig().buildType.getContainer());
            String joinedArgs = StringUtil.join(" ", strings.toArray(new String[0]));
            try {
                Process process = new ProcessBuilder(strings).start();
                StreamUtil.readConstantly(process.getInputStream(), output, 1024, "Container Output Reader " + competitor);
                StreamUtil.readConstantly(process.getErrorStream(), error, 1024, "Container Error Reader " + competitor);
                Log.TRACE.log("Starting docker instance with args: " + joinedArgs);
            } catch (IOException e) {
                Log.ERROR.log("IOException starting up Docker container for GameClient with args: " + joinedArgs, e);
                throw new NoReturnException();
            }
        }

        private void kill() {
            try {
                new ProcessBuilder("docker", "kill", "--signal=SIGKILL", this.containerName).start();
            } catch (IOException e) {
                Log.ERROR.log("IOException stopping docker container: " + this.containerName);
            }
        }
    }

    /**
     * Manages the network for a single game.
     */
    private class GameManager {// This will make it easier to run multiple games on a single machine.
        private final Match match;
        private final GameRules gameRules;
        private final List<ContainerProcess> dockerProcesses;
        private final Set<Competitor> competitorWaitList;
        private final AtomicBoolean gameEnd = new AtomicBoolean();
        private final AtomicLong matchStartTime = new AtomicLong();
        private final Map<Competitor, TransferSocket> competitorSocketMap;
        private final Map<TransferSocket, Competitor> socketCompetitorMap;
        private final GameRules.GameActionHandler gameActionHandler;

        private GameManager(Match match, Consumer<Team> onVictory) {
            this.match = match;
            try {
                this.gameRules = Config.gameRules.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                Log.ERROR.log("Unable to initialize GameRule", e);
                throw new NoReturnException();
            }
            this.competitorWaitList = new HashSet<>(this.match.getCompetitors());
            this.competitorSocketMap = new HashMap<>(this.competitorWaitList.size());
            this.socketCompetitorMap = new HashMap<>(this.competitorWaitList.size());
            this.gameRules.initForMatch(this.match);
            this.gameActionHandler = this.gameRules.setupNetworking((competitor, update, objects) -> this.competitorSocketMap.get(competitor).write(MachineAction.GAME_UPDATE, update, objects), onVictory);
            this.dockerProcesses = this.competitorWaitList.stream().map(competitor -> startupRunnerInstance(competitor, this.gameRules)).collect(Collectors.toList());
        }

        /**
         * Kill all competitor containers.
         */
        private void kill() {
            this.gameEnd.set(true);
            this.socketCompetitorMap.keySet().forEach(TransferSocket::close);
            this.dockerProcesses.forEach(ContainerProcess::kill);
        }

        /**
         * Setup the connection between {@link Competitor} and {@link TransferSocket}.
         *
         * @param competitor the {@link Competitor} communicating using the {@link TransferSocket}.
         * @param socket     the {@link TransferSocket} the {@link Competitor} is using.
         */
        private void relateConnection(Competitor competitor, TransferSocket socket) {
            this.competitorSocketMap.put(competitor, socket);
            this.socketCompetitorMap.put(socket, competitor);
        }

        /**
         * Marks the {@link Competitor} using the provided {@link TransferSocket} as ready to compete.
         * <p>
         * Starts the game start if it is the last {@link Competitor} to report ready.
         *
         * @param socket the {@link TransferSocket} used for communication that the communicating {@link Competitor} is ready.
         */
        private void markReady(TransferSocket socket) {
            this.competitorWaitList.remove(this.socketCompetitorMap.get(socket));
            if (this.competitorWaitList.isEmpty()) {
                Long time = this.gameRules.getMatchStartTime();
                this.matchStartTime.set(time);
                this.socketCompetitorMap.keySet().forEach(transferSocket -> transferSocket.write(MachineAction.GAME_UPDATE, GameUpdate.GAME_START, new GameArguments(time)));
            }
        }

        /**
         * Handles when a {@link Competitor} sends a message
         *
         * @param transferSocket the {@link TransferSocket} sending the {@link GameAction} {@link MachineAction}.
         * @param action         the {@link GameAction} the {@link Competitor} wants to make.
         * @param arguments      the wrapper representing the arguments.
         */
        private void handle(TransferSocket transferSocket, GameAction action, GameArguments arguments) {
            if (System.currentTimeMillis() < this.matchStartTime.get()) return;// Drop actions before start time.
            this.gameActionHandler.handle(this.socketCompetitorMap.get(transferSocket), action, arguments);
        }
    }
}
