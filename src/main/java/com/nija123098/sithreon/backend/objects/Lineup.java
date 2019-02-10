package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.util.StringUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a list of {@link Repository} instances with no version.
 *
 * @see Team
 */
public class Lineup implements Comparable<Lineup> {
    private final List<Repository> repositories;

    public Lineup(List<Repository> repositories) {
        if (repositories == null) this.repositories = null;
        else {
            List<Repository> list = new ArrayList<>(repositories);
            list.sort(Comparator.naturalOrder());
            this.repositories = Collections.unmodifiableList(list);
        }
    }

    public Lineup(String members) {
        this(Stream.of(members.split(Pattern.quote("+"))).map(s -> {
            int position = s.indexOf('#');
            return position == -1 ? s : s.substring(0, position);
        }).map(Repository::getRepo).collect(Collectors.toList()));
    }

    /**
     * Gets the {@link Repository} instances represented by this instance.
     *
     * @return the {@link Repository} instances.
     */
    public List<Repository> getRepositories() {
        return this.repositories;
    }

    /**
     * Gets the number of {@link Repository} instances on the lineup.
     *
     * @return the size of the lineup.
     */
    public Integer getLineupSize() {
        return this.repositories.size();
    }

    @Override
    public String toString() {
        return StringUtil.join("+", getRepositories().stream().map(Repository::toString).toArray(String[]::new));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lineup lineup = (Lineup) o;
        return Objects.equals(this.repositories, lineup.repositories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.repositories);
    }

    @Override
    public int compareTo(Lineup o) {
        int comparison;
        int minSize = Math.min(this.getRepositories().size(), o.getRepositories().size());
        for (int i = 0; i < minSize; i++) {
            comparison = this.getRepositories().get(i).compareTo(o.getRepositories().get(i));
            if (comparison != 0) return comparison;
        }
        return this.getRepositories().size() - o.getRepositories().size();
    }
}
