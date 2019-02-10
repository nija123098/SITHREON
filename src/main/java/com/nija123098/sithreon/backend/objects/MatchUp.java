package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.util.StringUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A object representing a list of {@link Lineup}s to battle.
 * <p>
 * A {@link Match} is a specific version of this.
 *
 * @see Match
 */
public class MatchUp {

    /**
     * The competing repositories.
     */
    private final List<Lineup> lineups;

    public MatchUp(List<Lineup> lineups) {
        if (lineups == null) this.lineups = null;
        else {
            List<Lineup> list = new ArrayList<>(lineups);
            list.sort(Comparator.comparing(Lineup::toString));
            this.lineups = Collections.unmodifiableList(list);
        }// Match must compare for queuing, so this can not have it's own comparator implementation
    }

    /**
     * Constructs a match up of a list of lineups.
     */
    public MatchUp(String lineups) {
        this(Stream.of(lineups.split(Pattern.quote("|"))).map(Lineup::new).collect(Collectors.toList()));
    }

    /**
     * Gets the list of competing lineups for this instance.
     *
     * @return the list of competing lineups.
     */
    public List<Lineup> getLineups() {
        return this.lineups;
    }

    /**
     * Check if this {@link MatchUp} contains the provided {@link Repository}
     * in any of it's {@link Lineup}s.
     *
     * @param repository the {@link Repository} to check for.
     * @return if the {@link MatchUp} contains the {@link Repository}.
     */
    public boolean containsRepo(Repository repository) {
        return this.lineups.stream().anyMatch(lineup -> lineup.getRepositories().contains(repository));
    }

    @Override
    public String toString() {
        return StringUtil.join("|", lineups.stream().map(lineup -> StringUtil.join("+", lineup.getRepositories().stream().map(Object::toString).toArray(String[]::new))).toArray(String[]::new));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchUp matchUp = (MatchUp) o;
        return Objects.equals(this.lineups, matchUp.lineups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.lineups);
    }
}
