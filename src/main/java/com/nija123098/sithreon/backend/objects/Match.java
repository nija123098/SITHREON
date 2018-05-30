package com.nija123098.sithreon.backend.objects;

import java.util.Objects;

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
     * The hashes expected to be up to date and approved.
     */
    private final String firstHash, secondHash;

    /**
     * Constructs a standard match whose repository order will be sorted
     * according to {@link Repository#compareTo(Repository)}.
     *
     * @param one       one {@link Repository}.
     * @param other     another {@link Repository}.
     * @param oneHash   the expected and approved HEAD hash of the one {@link Repository}.
     * @param otherHash the expected and approved HEAD hash of the other {@link Repository}.
     * @param time      the time the match was initially scheduled.
     */
    public Match(Repository one, Repository other, String oneHash, String otherHash, long time) {
        super(one, other);
        if (this.getFirst().equals(one)) {
            this.firstHash = oneHash;
            this.secondHash = otherHash;
        } else {
            this.firstHash = otherHash;
            this.secondHash = oneHash;
        }
        this.time = time;
    }

    /**
     * Returns the expected HEAD hash of the {@link Match#getFirst()} {@link Repository}.
     *
     * @return the expected HEAD hash of the {@link Match#getFirst()} {@link Repository}.
     */
    public String getFirstHash() {
        return this.firstHash;
    }

    /**
     * Returns the expected HEAD hash of the {@link Match#getSecond()} {@link Repository}.
     *
     * @return the expected HEAD hash of the {@link Match#getSecond()} {@link Repository}.
     */
    public String getSecondHash() {
        return this.secondHash;
    }

    /**
     * Gets the highest priority of the two {@link Repository} instances.
     *
     * @return the highest priority of the two {@link Repository} instances.
     */
    private int getHighPriority() {
        return Math.max(this.getFirst().getPriority(), this.getSecond().getPriority());
    }

    /**
     * Gets the lowest priority of the two {@link Repository} instances.
     *
     * @return the lowest priority of the two {@link Repository} instances.
     */
    private int getLowPriority() {
        return Math.min(this.getFirst().getPriority(), this.getSecond().getPriority());
    }

    /**
     * Gets the {@link MatchUp} for this instance.
     *
     * @return the {@link MatchUp} for this instance.
     */
    public MatchUp getMatchUp() {
        return new MatchUp(this.getFirst(), this.getSecond());
    }

    @Override
    public int compareTo(Match o) {
        if (o.getLowPriority() != this.getLowPriority()) return this.getLowPriority() - o.getLowPriority();
        if (this.getHighPriority() != o.getHighPriority()) return this.getHighPriority() - o.getHighPriority();
        return this.time > o.time ? 1 : -1;
    }

    @Override
    public String toString() {
        return super.toString() + "+" + this.firstHash + "+" + this.secondHash + "+" + this.time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Match other = (Match) o;
        return super.equals(other) && this.time == other.time && this.firstHash.equals(other.firstHash) && this.secondHash.equals(other.secondHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.firstHash, this.secondHash, this.time);
    }
}
