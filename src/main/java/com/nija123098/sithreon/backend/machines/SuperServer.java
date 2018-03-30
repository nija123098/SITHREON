package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.command.CommandMethod;
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
import java.util.stream.Stream;

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
     *
     * The {@link Set} may contain {@link Repository}s that have been updated and not approved and have not been checked for changes.
     */
    private final Set<Repository> approvedRepos = new HashSet<>();

    /**
     * The {@link DualPriorityResourceManager} for managing {@link Repository}s and {@link CodeCheckClient} {@link TransferSocket}s.
     * No concern is given over priority of {@link Repository}s, as code checking should be a very quick process.
     * {@link CodeCheckClient}s are sorted by their priority as reported by their initial connection.
     */
    private final DualPriorityResourceManager<Repository, TransferSocket> codeCheckResourceManager = new DualPriorityResourceManager<>(((repository, socket) -> {
        this.reposUnderReview.add(repository);
        socket.write(MachineAction.CHECK_REPO, repository);
    }));

    /**
     * The {@link DualPriorityResourceManager} for {@link Match}s and {@link GameServer} {@link TransferSocket}s.
     * {@link Match}s are sorted according to {@link Match#compareTo(Match)} based on {@link PriorityLevel} and time.
     * {@link GameServer}s are sorted by their priority as reported by their initial connection.
     */
    private final DualPriorityResourceManager<Match, TransferSocket> gameRunnerResourceManager = new DualPriorityResourceManager<>((match, socket) -> {
        this.activeMatches.put(match, socket);
        socket.write(MachineAction.RUN_GAME, match);
    });

    /**
     * The {@link ScheduledExecutorService} responsible for checking for {@link Repository} updates.
     */
    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1, r -> ThreadMaker.getThread(ThreadMaker.BACKEND, "Update Checker", true, r));

    /**
     * A {@link Set} of {@link Repository}s currently under review by {@link CodeCheckClient}s.
     */
    private final Set<Repository> reposUnderReview = new HashSet<>();

    /**
     * The {@link Queue} responsible for storing the order of {@link Repository}s to check for updates.
     *
     * When a {@link Repository} is checked it will be removed from the head of the queue and added to the end.
     */
    private final Queue<Repository> checkRepositoryQueue = new ArrayDeque<>();

    /**
     * The {@link Map} for {@link Match}s to {@link TransferSocket}s
     * belonging to {@link GameServer}s which are currently running
     * {@link Match}s according to its value.
     */
    private final Map<Match, TransferSocket> activeMatches = new HashMap<>();
    public SuperServer() {
        Database.init();
        this.checkRepositoryQueue.addAll(Database.REGISTERED_REPOS.keySet());
        new SocketAcceptor(this);
        this.executorService.scheduleWithFixedDelay(() -> {
            Repository repository = this.checkRepositoryQueue.poll();
            if (repository == null) Log.WARN.log("Repository check queue empty");
            else {
                if (repository.isApproved()) this.approvedRepos.add(repository);
                else if (!this.codeCheckResourceManager.getFirstWaiting().contains(repository) && !repository.isUpToDate()) {
                    this.invalidateRepo(repository);
                    this.codeCheckResourceManager.giveFirst(repository);
                }// no action if the repository is up to date but not approved
                this.checkRepositoryQueue.add(repository);
            }
        }, 1, 1, TimeUnit.MINUTES);
        this.runOnClose(() -> {
            this.executorService.shutdownNow();
            Database.MATCHES_TO_DO.clear();
            Database.MATCHES_TO_DO.putAll(Stream.concat(this.gameRunnerResourceManager.getFirstWaiting().stream(), this.activeMatches.keySet().stream()).collect(Collectors.toMap(Function.identity(), i -> true)));
        });
    }

    @Override
    protected void notifyReady(ManagedMachineType machineType, TransferSocket socket){
        if (machineType == ManagedMachineType.CODE_CHECK) this.codeCheckResourceManager.giveSecond(socket);
        else if (machineType == ManagedMachineType.GAME_SERVER) this.gameRunnerResourceManager.giveSecond(socket);
        else super.notifyReady(machineType, socket);
    }

    /**
     * Activated at the response of a {@link CodeCheckClient} as to the result of security checks
     * to ensure the safety of the repository at the most recent HEAD for the repository.
     *
     * @param repository the repository checked.
     * @param hash the hash of the commit the repo was checked at.
     * @param result true if the repository passed safety checks, false otherwise.
     * @param report any additional comments regarding the result of the check.
     */
    @Action(MachineAction.REPO_CODE_REPORT)
    public void readReport(Repository repository, String hash, Boolean result, String report){
        Database.REPO_LAST_HEAD_HASH.put(repository, hash);
        Database.REPO_APPROVAL.put(repository, result);
        this.reposUnderReview.remove(repository);
        if (result){// validate repo
            this.approvedRepos.add(repository);
            this.getMatches(repository).forEach(this.gameRunnerResourceManager::giveFirst);
            Log.INFO.log("Repository " + repository + " passed code inspection: " + report);
        } else Log.INFO.log("Repository " + repository + " failed test: " + report);
    }

    /**
     * A {@link MachineAction} method of a {@link GameServer} that one of the {@link Repository}
     * instances in the {@link Match} it was assigned to HEAD is not at the expected commit.
     *
     * @param repository the repository out of date.
     */
    @Action(MachineAction.MATCH_OUT_OF_DATE)
    public void outOfDate(Repository repository){
        this.invalidateRepo(repository);
        if (!this.reposUnderReview.contains(repository) && !this.codeCheckResourceManager.getFirstWaiting().contains(repository)) this.codeCheckResourceManager.giveFirst(repository);
    }

    /**
     * Gets the list of {@link Match}s to be scheduled in response to the {@link Repository} update.
     *
     * @param updatedRepo the {@link Repository} to get the {@link Match}s for.
     * @return the list of {@link Match}s, in no particular order, to have played in response to the update.
     */
    private List<Match> getMatches(Repository updatedRepo){// round robin
        return this.approvedRepos.stream().filter(repository -> !repository.equals(updatedRepo)).map(repository -> new Match(repository, updatedRepo, repository.getLastCheckedHeadHash(), updatedRepo.getLastCheckedHeadHash(), System.currentTimeMillis())).collect(Collectors.toList());
    }

    /**
     * The {@link MachineAction} method to notify this instance that the {@link Match} has been completed.
     *
     * @param match the match played by the responding {@link GameServer}.
     * @param firstWin if the first repository was the victor of the match.
     */
    @Action(MachineAction.MATCH_COMPLETE)
    public void matchComplete(Match match, Boolean firstWin) {
        Database.FIRST_WINS.put(match, firstWin);
        Log.INFO.log("Match " + match + " complete and was won buy " + (firstWin ? match.getFirst() : match.getSecond()));
    }

    /**
     * A private helper method for organizing the process
     * of invalidating a {@link Repository} due to an update to it.
     *
     * @param repository the repository to invalidate.
     */
    private void invalidateRepo(Repository repository){
        this.approvedRepos.remove(repository);
        Database.FIRST_WINS.keySet().stream().filter(matchUp -> matchUp.getFirst().equals(repository) || matchUp.getSecond().equals(repository)).forEach(Database.FIRST_WINS::remove);
    }

    @CommandMethod
    public void registerRepository(Repository repository) {
        Database.REGISTERED_REPOS.put(repository, true);
        this.checkRepositoryQueue.add(repository);
    }
}
