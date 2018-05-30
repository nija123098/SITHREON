package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.*;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.DualPriorityResourceManager;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.PriorityLevel;
import com.nija123098.sithreon.backend.util.ThreadMaker;

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
     * A {@link Set} of all {@link Repository}s approved at the last check of their being updated.
     * <p>
     * The {@link Set} may contain {@link Repository}s that have been updated and not approved and have not been checked for changes.
     */
    private final Set<Repository> approvedRepos = new HashSet<>();

    /**
     * The {@link DualPriorityResourceManager} for managing {@link Repository}s and {@link CodeCheckClient} {@link TransferSocket}s.
     * No concern is given over priority of {@link Repository}s, as code checking should be a very quick process.
     * {@link CodeCheckClient}s are sorted by their priority as reported by their initial connection.
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
        this.checkRepositoryQueue.addAll(Database.REGISTERED_REPOS.keySet());
        new SocketAcceptor(this, Config.externalPort);
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, r -> ThreadMaker.getThread(ThreadMaker.BACKEND, "Update Checker", true, r));
        executorService.scheduleWithFixedDelay(() -> {// checks if the next repo in the que has been updated
            Repository repository = this.checkRepositoryQueue.poll();
            if (repository == null) Log.WARN.log("Repository check queue empty");
            else {
                if (repository.isApproved()) this.approvedRepos.add(repository);// should already contain it
                else if (!this.codeCheckResourceManager.getFirstWaiting().contains(repository) && !repository.isUpToDate()) {
                    this.invalidateRepo(repository);
                    this.codeCheckResourceManager.giveFirst(repository);
                }// no action if the repository is up to date but not approved
                this.checkRepositoryQueue.add(repository);
            }
        }, 1, 1, TimeUnit.MINUTES);
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
     * Activated at the response of a {@link CodeCheckClient} as to the result of security checks
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
        if (result) {// validate repo
            this.approvedRepos.add(repository);
            List<Match> matches = this.getMatches(repository);
            Database.MATCHES_TO_DO.putAll(matches.stream().collect(Collectors.toMap(Function.identity(), i -> true)));
            matches.forEach(this.gameRunnerResourceManager::giveFirst);
            Log.INFO.log("Repository " + repository + " of hash " + hash + " passed code inspection: " + report);
        } else Log.INFO.log("Repository " + repository + " failed test: " + report);
    }

    /**
     * Gets the list of {@link Match}s to be scheduled in response to the {@link Repository} update.
     *
     * @param updatedRepo the {@link Repository} to get the {@link Match}s for.
     * @return the list of {@link Match}s, in no particular order, to have played in response to the update.
     */
    private List<Match> getMatches(Repository updatedRepo) {// round robin
        return this.approvedRepos.stream().filter(repository -> !repository.equals(updatedRepo)).map(repository -> new Match(repository, updatedRepo, repository.getLastCheckedHeadHash(), updatedRepo.getLastCheckedHeadHash(), System.currentTimeMillis())).collect(Collectors.toList());
    }

    /**
     * The {@link MachineAction} method to notify this instance that the {@link Match} has been completed.
     *
     * @param match    the match played by the responding {@link GameServer}.
     * @param firstWin if the first repository was the victor of the match.
     */
    @Action(MachineAction.MATCH_COMPLETE)
    public void matchComplete(Match match, Boolean firstWin, TransferSocket socket) {
        Database.MATCHES_TO_DO.remove(match);
        Database.FIRST_WINS.put(match.getMatchUp(), firstWin);// must insert MatchUps
        Log.INFO.log("Match " + match + " complete and was won by " + (firstWin ? match.getFirst() : match.getSecond()));
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
            if (match.getFirst().equals(repository) || match.getSecond().equals(repository)) {
                TransferSocket transferSocket = this.matchesInProgress.remove(match);
                if (transferSocket != null) {
                    transferSocket.write(MachineAction.MATCH_OUT_OF_DATE, repository);// for thread safety
                }
            }
        });
        Database.FIRST_WINS.keySet().removeIf(matchUp -> matchUp.getFirst().equals(repository) || matchUp.getSecond().equals(repository));
    }

    public void registerRepository(Repository repository) {
        if (Database.REGISTERED_REPOS.get(repository)) Log.INFO.log("Repo already registered");
        else {
            Database.REGISTERED_REPOS.put(repository, true);
            this.checkRepositoryQueue.addFirst(repository);
        }
    }
}
