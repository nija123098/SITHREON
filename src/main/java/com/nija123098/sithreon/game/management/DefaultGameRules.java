package com.nija123098.sithreon.game.management;

import com.nija123098.sithreon.backend.objects.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultGameRules implements GameRules {
    private final AtomicReference<Match> match = new AtomicReference<>();
    @Override
    public List<Match> getMatches(Set<Repository> approvedRepos, Repository updatedRepo) {
        return approvedRepos.stream().filter(repository -> !repository.equals(updatedRepo)).map(repository -> new Match(Arrays.asList(new Team(Collections.singletonList(new TeamMember(repository, repository.getLastCheckedHeadHash()))), new Team(Collections.singletonList(new TeamMember(repository, repository.getLastCheckedHeadHash())))), System.currentTimeMillis())).collect(Collectors.toList());
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
    public GameActionHandler setupNetworking(GameUpdateHandler gameActionHandler, Consumer<Team> onVictory) {
        List<Team> teams = new LinkedList<>(this.match.get().getTeams());
        return (competitor, action, objects) -> {
            boolean win = false;
            synchronized (teams) {
                for (int i = 0; i < teams.size(); i++) {
                    if (teams.get(i).getTeamNumber() == competitor.getTeamNumber()) {
                        if (teams.size() == 2) win = true;
                        teams.remove(i);
                        break;
                    }
                }
                if (win) onVictory.accept(teams.get(0));
            }
        };
    }

    @Override
    public Long getMatchStartTime() {
        return System.currentTimeMillis() + 10_000;
    }
}
