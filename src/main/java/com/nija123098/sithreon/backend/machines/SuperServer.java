package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.*;
import com.nija123098.sithreon.backend.objects.Lineup;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.PriorityLevel;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.DualPriorityResourceManager;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.ThreadMaker;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.game.management.GameRules;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The {@link Machine} representation of the main coordination server.
 * It is responsible for coordinating the checking and running of
 * competitor's code in order to order the competitor's AIs.
 *
 * @author nija123098
 */
public class SuperServer extends Machine {
    /**
     * The game rules for other servers to use
     */
    private final GameRules gameRules;

    /**
     * A {@link Set} of all {@link Repository}s approved at the last check of their being updated.
     * <p>
     * The {@link Set} may contain {@link Repository}s that have been updated and not approved and have not been checked for changes.
     */
    private final Set<Repository> approvedRepos = new HashSet<>();

    /**
     * The {@link DualPriorityResourceManager} for managing {@link Repository}s and {@link CheckClient} {@link TransferSocket}s.
     * No concern is given over priority of {@link Repository}s, as code checking should be a very quick process.
     * {@link CheckClient}s are sorted by their priority as reported by their initial connection.
     */
    private final DualPriorityResourceManager<Repository, TransferSocket> codeCheckResourceManager = new DualPriorityResourceManager<>(((repository, socket) -> {
        socket.write(MachineAction.CHECK_REPO, repository);
        socket.setOnClose(() -> this.codeCheckResourceManager.giveFirst(repository));
    }));

    /**
     * The {@link DualPriorityResourceManager} for {@link Match}s and {@link GameServer} {@link TransferSocket}s.
     * {@link Match}s are sorted according to {@link Match#compareTo(Match)} based on {@link PriorityLevel} and time.
     * {@link GameServer}s are sorted by their priority as reported by their initial connection.
     */
    private final DualPriorityResourceManager<Match, TransferSocket> gameRunnerResourceManager = new DualPriorityResourceManager<>((match, socket) -> {
        socket.write(MachineAction.RUN_GAME, match);
        socket.setOnClose(() -> {// nulled on completion
            this.gameRunnerResourceManager.giveFirst(match);
            this.matchesInProgress.remove(match);
        });
        this.matchesInProgress.put(match, socket);
    });

    /**
     * A {@link Map} of the {@link Match}s under way, paired with the {@link TransferSocket} of the {@link GameServer} running it.
     */
    private final Map<Match, TransferSocket> matchesInProgress = new HashMap<>();

    /**
     * The {@link Queue} responsible for storing the order of {@link Repository}s to check for updates.
     * <p>
     * When a {@link Repository} is checked it will be removed from the head of the queue and added to the end.
     */
    private final Deque<Repository> checkRepositoryQueue = new LinkedList<>();

    public SuperServer() {
        Database.init();
        try {
            this.gameRules = Config.gameRules.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            Log.ERROR.log("Unable to initialize GameRule", e);
            throw new NoReturnException();
        }
        this.checkRepositoryQueue.addAll(Database.REGISTERED_REPOS.keySet());
        new SocketAcceptor(this, Config.externalPort);
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, r -> ThreadMaker.getThread(ThreadMaker.BACKEND, "Repository Update Checker", true, r));
        executorService.scheduleWithFixedDelay(() -> {// checks if the next repo in the que has been updated
            Repository repository = this.checkRepositoryQueue.poll();
            if (repository == null) Log.WARN.log("Repository check queue empty");
            else try {
                if (repository.isApproved()) this.approvedRepos.add(repository);// should already contain it
                else if (!this.codeCheckResourceManager.getFirstWaiting().contains(repository) && !repository.getRepositoryConfig().disabled && !repository.isUpToDate()) {
                    Log.DEBUG.log("Adding " + repository + " to check queue");
                    this.invalidateRepo(repository);
                    this.codeCheckResourceManager.giveFirst(repository);
                }// no action if the repository is up to date but not approved
                this.checkRepositoryQueue.add(repository);
            } catch (Exception t) {
                Log.WARN.log("Exception checking if repository was up to date: " + repository.toString(), t);
            }
        }, Config.checkInterval, Config.checkInterval, TimeUnit.MILLISECONDS);
        this.runOnClose(executorService::shutdownNow);
    }

    @Override
    protected void notifyReady(ManagedMachineType machineType, TransferSocket socket) {
        if (machineType == ManagedMachineType.CODE_CHECK) {
            this.codeCheckResourceManager.giveSecond(socket);
            socket.setOnClose(() -> this.codeCheckResourceManager.removeSecond(socket));
        } else if (machineType == ManagedMachineType.GAME_SERVER) {
            this.gameRunnerResourceManager.giveSecond(socket);
            socket.setOnClose(() -> this.gameRunnerResourceManager.removeSecond(socket));
        } else super.notifyReady(machineType, socket);
    }

    /**
     * Activated at the response of a {@link CheckClient} as to the result of security checks
     * to ensure the safety of the repository at the most recent HEAD for the repository.
     *
     * @param repository the repository checked.
     * @param hash       the hash of the commit the repo was checked at.
     * @param result     true if the repository passed safety checks, false otherwise.
     * @param report     any additional comments regarding the result of the check.
     */
    @Action(MachineAction.REPO_CODE_REPORT)
    public void readReport(Repository repository, String hash, Boolean result, String report, TransferSocket socket) {
        Database.REPO_LAST_HEAD_HASH.put(repository, hash);
        Database.REPO_APPROVAL.put(repository, result);
        socket.setOnClose(null);
        if (!result) Log.INFO.log("Repository " + repository + " failed test: " + report);
        else {
            Log.INFO.log("Repository " + repository + " of hash " + hash + " passed code inspection: " + report);
            this.approvedRepos.add(repository);
            List<Match> matches = this.gameRules.getMatches(this.approvedRepos, repository);
            Database.MATCHES_TO_DO.putAll(matches.stream().collect(Collectors.toMap(Function.identity(), i -> true)));
            matches.forEach(this.gameRunnerResourceManager::giveFirst);
        }
    }

    /**
     * The {@link MachineAction} method to notify this instance that the {@link Match} has been completed.
     *
     * @param match   the match played by the responding {@link GameServer}.
     * @param winners the winners of the match.
     */
    @Action(MachineAction.MATCH_COMPLETE)
    public void matchComplete(Match match, Lineup winners, TransferSocket socket) {
        Database.MATCHES_TO_DO.remove(match);
        Database.MATCHUP_WINNERS.put(match.getMatchUp(), winners);// must insert MatchUps
        Log.INFO.log("Match " + match + " complete and was won by " + winners);
        socket.setOnClose(null);
    }

    /**
     * A private helper method for organizing the process
     * of invalidating a {@link Repository} due to an update to it.
     *
     * @param repository the repository to invalidate.
     */
    private void invalidateRepo(Repository repository) {
        this.approvedRepos.remove(repository);
        new HashMap<>(this.matchesInProgress).forEach((match, socket) -> {
            if (match.containsRepo(repository)) {
                TransferSocket transferSocket = this.matchesInProgress.remove(match);
                if (transferSocket != null) {
                    transferSocket.write(MachineAction.MATCH_OUT_OF_DATE, repository);// for thread safety
                }
            }
        });
        Database.MATCHUP_WINNERS.keySet().removeIf(matchUp -> matchUp.containsRepo(repository));
    }

    /**
     * Registers the {@link Repository} to be part of the competition.
     *
     * @param repository the {@link Repository} to be part of the competition.
     */
    public void registerRepository(Repository repository) {
        if (Database.REGISTERED_REPOS.get(repository)) Log.INFO.log("Repo already registered");
        else {
            Database.REGISTERED_REPOS.put(repository, true);
            this.checkRepositoryQueue.addFirst(repository);
        }
    }
}
