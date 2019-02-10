package com.nija123098.sithreon.game.management;

import com.nija123098.sithreon.backend.objects.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultGameRules implements GameRules {
    private final AtomicReference<Match> match = new AtomicReference<>();

    @Override
    public List<Match> getMatches(Set<Repository> approvedRepos, Repository updatedRepo) {
        return approvedRepos.stream().filter(repository -> !repository.equals(updatedRepo)).map(repository -> new Match(Arrays.asList(new Team(Collections.singletonList(new TeamMember(repository, repository.getLastCheckedHeadHash()))), new Team(Collections.singletonList(new TeamMember(updatedRepo, updatedRepo.getLastCheckedHeadHash())))), System.currentTimeMillis())).collect(Collectors.toList());// time probably shouldn't be part of the hash
    }

    @Override
    public void initForMatch(Match match) {
        this.match.set(match);
    }

    @Override
    public List<String> getDockerRules(Competitor competitor) {
        return Collections.emptyList();
    }

    @Override
    public GameActionHandler setupNetworking(GameUpdateHandler gameUpdateHandler, Consumer<Team> onVictory) {
        return (competitor, action, objects) -> {// todo implement competition
            if (action == GameAction.SURRENDER) {
                onVictory.accept(this.match.get().getTeams().get(competitor.getTeamNumber() == 0 ? 1 : 0));
            }
        };
    }

    @Override
    public Long getMatchStartTime() {
        return System.currentTimeMillis() + 10_000;
    }
}
