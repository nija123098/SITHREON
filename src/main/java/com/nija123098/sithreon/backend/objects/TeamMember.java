package com.nija123098.sithreon.backend.objects;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Represents a versioned {@link Repository} within a {@link Team}.
 *
 * @see Competitor
 */
public class TeamMember implements Comparable<TeamMember> {
    private final String hash;
    private final Repository repository;
    private final AtomicReference<Team> team = new AtomicReference<>();

    public TeamMember(Repository repository, String hash) {
        this.repository = repository;
        this.hash = hash;
    }

    public TeamMember(String repoHash) {
        String[] split = repoHash.split(Pattern.quote("#"));
        this.repository = Repository.getRepo(split[0]);
        this.hash = split[1];
    }

    /**
     * Gets the {@link Repository} of the {@link TeamMember}.
     *
     * @return the {@link Repository} of the instance.
     */
    public Repository getRepository() {
        return this.repository;
    }

    /**
     * The version of the instance as a hash.
     *
     * @return the version hash.
     */
    public String getHash() {
        return this.hash;
    }

    /**
     * The {@link Team} the instance belongs to.
     *
     * @return the {@link Team}.
     */
    private Team getTeam() {
        return this.team.get();
    }

    /**
     * The instance's {@link Team} number.
     *
     * @return the {@link Team} number.
     */
    public int getTeamNumber() {
        return this.getTeam().getTeamNumber();
    }

    /**
     * Sets the {@link Team} for reference.
     *
     * @param team the {@link Team} this instance belongs to.
     */
    void setTeam(Team team) {
        this.team.set(team);
    }

    @Override
    public String toString() {
        return this.repository.toString() + "#" + this.hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMember that = (TeamMember) o;
        return Objects.equals(this.hash, that.hash) &&
                Objects.equals(this.repository, that.repository);// Hypothetically repository check isn't needed
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.hash, this.repository);
    }

    @Override
    public int compareTo(TeamMember o) {
        return this.repository.compareTo(o.repository);
    }
}
