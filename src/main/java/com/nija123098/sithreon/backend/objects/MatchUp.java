package com.nija123098.sithreon.backend.objects;

import java.util.Objects;

/**
 * A object representing a pairing of two competitors.
 * <p>
 * The {@link Repository}s competing will be sorted
 * according to the {@link Repository#compareTo(Repository)}.
 */
public class MatchUp {

    /**
     * The competing repositories.
     */
    private final Repository first, second;

    /**
     * Constructs a pairing of two competitors which is sorted
     * in order of guaranteeing that in the same airing the first
     * competitor will always be the first listed competitor.
     *
     * @param one   a {@link Repository}.
     * @param other another {@link Repository}.
     */
    public MatchUp(Repository one, Repository other) {
        if (one.compareTo(other) < 0) {
            this.first = one;
            this.second = other;
        } else {
            this.first = other;
            this.second = one;
        }
    }

    /**
     * Returns the first competing {@link Repository}.
     *
     * @return the first competing {@link Repository}.
     */
    public Repository getFirst() {
        return this.first;
    }

    /**
     * Returns the second competing {@link Repository}.
     *
     * @return the second competing {@link Repository}.
     */
    public Repository getSecond() {
        return this.second;
    }

    @Override
    public String toString() {
        return this.first + "+" + this.second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchUp other = (MatchUp) o;
        return this.first.equals(other.first) && this.second.equals(other.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.first, this.second);
    }
}
