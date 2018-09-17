package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.util.StringUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The object specifying a match between two {@link Repository}
 * instances including the hash of the approved commit of each.
 */
public class Match extends MatchUp implements Comparable<Match> {

    /**
     * The time to be used as a tie breaker for scheduling priority.
     */
    private final long time;

    /**
     * List of {@link List} competing.
     */
    private final List<Team> teams;

    /**
     * Constructs a standard match whose repository order will be sorted
     * according to {@link Repository#compareTo(Repository)}.
     *
     * @param teams the list of teams competing in this match.
     * @param time  the time the match was initially scheduled.
     */
    public Match(List<Team> teams, long time) {
        super((List<Lineup>) null);
        List<Team> list = new ArrayList<>(teams);
        list.sort(Comparator.comparing(Lineup::toString));
        this.teams = Collections.unmodifiableList(list);
        this.time = time;
        this.setupTeams();
    }

    public Match(String s) {
        super((List<Lineup>) null);
        String[] split = s.split(Pattern.quote("+"));
        this.teams = new ArrayList<>(split.length - 1);
        for (int i = 0; i < split.length - 1; i++)
            this.teams.add(new Team(split[i]));
        this.time = Long.parseLong(split[split.length - 1]);
        this.teams.sort(Comparator.naturalOrder());
        this.setupTeams();
    }

    private void setupTeams() {
        AtomicInteger teamNumber = new AtomicInteger();
        this.teams.forEach(team -> team.setTeamNumber(teamNumber.getAndIncrement()));
    }

    /**
     * Gets the list of teams competing.
     *
     * @return the list of teams competing.
     */
    public List<Team> getTeams() {
        return this.teams;
    }

    /**
     * Gets the highest priority of all competitors.
     *
     * @return the highest priority of all competitors.
     */
    private int getHighPriority() {
        return this.getPriority(Math::max);
    }

    /**
     * Gets the lowest priority of all competitors.
     *
     * @return the lowest priority of all competitors.
     */
    private int getLowPriority() {
        return this.getPriority(Math::min);
    }

    /**
     * Runs a get priority based equally on each {@link TeamMember}.
     *
     * @param function the priority operator.
     * @return the priority according to the binary operator.
     */
    private int getPriority(BinaryOperator<Integer> function) {
        return this.teams.stream().flatMap(team -> team.getMembers().stream()).map(TeamMember::getRepository).map(Repository::getPriority).reduce(0, function);
    }

    /**
     * Gets the {@link MatchUp} for this instance.
     *
     * @return the {@link MatchUp} for this instance.
     */
    public MatchUp getMatchUp() {
        return new MatchUp(new ArrayList<>(this.teams));
    }

    public List<Competitor> getCompetitors() {
        String matchId = StringUtil.getSha256Hash(this.toString());// Makes it shorter
        return this.teams.stream().flatMap(team -> team.getMembers().stream()).map(teamMember -> new Competitor(teamMember.getRepository(), teamMember.getHash(), matchId, teamMember.getTeamNumber())).collect(Collectors.toList());
    }

    @Override
    public int compareTo(Match o) {
        if (o.getLowPriority() != this.getLowPriority()) return this.getLowPriority() - o.getLowPriority();
        if (this.getHighPriority() != o.getHighPriority()) return this.getHighPriority() - o.getHighPriority();
        return this.time > o.time ? 1 : -1;
    }

    @Override
    public String toString() {
        return StringUtil.join("+", this.teams.stream().map(Team::toString).toArray(String[]::new)) + "+" + this.time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Match match = (Match) o;
        return this.time == match.time && this.teams.equals(match.teams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.time);
    }
}
