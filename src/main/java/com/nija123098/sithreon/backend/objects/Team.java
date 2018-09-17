package com.nija123098.sithreon.backend.objects;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Team extends Lineup {
    private final List<TeamMember> members;
    private transient final AtomicInteger teamNumber = new AtomicInteger(-1);// Should be set by Match init.

    public Team(List<TeamMember> members) {
        super(members.stream().map(TeamMember::getRepository).collect(Collectors.toList()));
        List<TeamMember> list = new ArrayList<>(members);
        list.sort(Comparator.naturalOrder());
        this.members = Collections.unmodifiableList(list);
        this.members.forEach(teamMember -> teamMember.setTeam(this));
    }

    public Team(String members) {
        super(members);
        this.members = Collections.unmodifiableList(Stream.of(members.split(Pattern.quote("+"))).map(s -> {
            String[] split = s.split(Pattern.quote("#"));
            return new TeamMember(Repository.getRepo(split[0]), split[1]);
        }).sorted(Comparator.naturalOrder()).collect(Collectors.toList()));
        this.members.forEach(teamMember -> teamMember.setTeam(this));
    }

    @Override
    public List<Repository> getRepositories() {
        return this.members.stream().map(TeamMember::getRepository).collect(Collectors.toList());
    }

    /**
     * Gets a list of members of this instance.
     *
     * @return the instance members.
     */
    public List<TeamMember> getMembers() {
        return this.members;
    }

    /**
     * Gets the {@link Lineup} this team is based on.
     *
     * @return the {@link Lineup} the team is based on.
     */
    public Lineup getLineup() {
        return new Lineup(this.getRepositories());
    }

    /**
     * Gets the number of this team.
     *
     * @return the team number.
     */
    public Integer getTeamNumber() {
        return teamNumber.get();
    }

    /**
     * Sets the team number for reference.
     *
     * @param teamNumber the team number.
     */
    void setTeamNumber(int teamNumber) {
        this.teamNumber.set(teamNumber);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < this.members.size() - 1; i++)
            builder.append(this.members.get(i)).append("+");
        builder.append(this.members.get(this.members.size() - 1));
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(this.members, team.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.members);
    }

    @Override
    public int compareTo(Lineup o) {
        return super.compareTo(o);
    }
}
