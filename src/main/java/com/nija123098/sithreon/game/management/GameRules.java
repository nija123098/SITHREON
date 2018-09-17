package com.nija123098.sithreon.game.management;

import com.nija123098.sithreon.backend.objects.Competitor;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.objects.Team;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface GameRules {
    /**
     * Gets the list of {@link Match}s to be scheduled in response to the {@link Repository} update.
     *
     * @param updatedRepo the {@link Repository} to get the {@link Match}s for.
     * @return the list of {@link Match}s, in no particular order, to have played in response to the update.
     */
    List<Match> getMatches(Set<Repository> approvedRepos, Repository updatedRepo);

    /**
     * Initializes the instance for the given {@link Match}.
     *
     * @param match the {@link Match} to initialize for.
     */
    void initForMatch(Match match);

    /**
     * Gets the full docker rules
     *
     * @param competitor the {@link Competitor} to add Docker rules for.
     * @return a list of strings to add as Docker rules.
     */
    List<String> getDockerRules(Competitor competitor);

    /**
     * Prepares the networking.
     *
     * @param gameUpdateHandler a {@link GameUpdateHandler} for sending out {@link GameUpdate}s.
     * @param onVictory a handler for when a {@link Team} is victorious.
     * @return a {@link GameActionHandler} for providing a way to register {@link GameAction}s without calling a instance here.
     */
    GameActionHandler setupNetworking(GameUpdateHandler gameUpdateHandler, Consumer<Team> onVictory);

    /**
     * Gets the time the match is to start.
     *
     * @return the time the match is to start.
     */
    Long getMatchStartTime();

    @FunctionalInterface
    interface GameUpdateHandler {
        void handle(Competitor competitor, GameUpdate update, GameArguments arguments);
    }
    @FunctionalInterface
    interface GameActionHandler {
        void handle(Competitor competitor, GameAction action, GameArguments arguments);
    }
}
