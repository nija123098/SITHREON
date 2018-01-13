package com.nija123098.sithreon.backend.util;

import org.eclipse.egit.github.core.service.RepositoryService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An instance representative of a Github repository.
 *
 * @author nija123098
 */
public class Repository {
    /**
     * The {@link RepositoryService} for getting
     * {@link org.eclipse.egit.github.core.Repository} instances.
     */
    private static final RepositoryService REPOSITORY_SERVICE = new RepositoryService();
    /**
     * A cache of repository objects.
     */
    private static final Map<String, Repository> CACHE = new HashMap<>();
    /**
     * The identifications for the repository.
     */
    private String repo, owner, name;

    private Repository(String repo) {
        this.repo = repo;
        String[] split = this.repo.split("/");
        this.owner = split[0];
        this.name = split[1];
    }

    /**
     * Requires the a repository instance or null if the specified repo does not exist.
     *
     * @param repo the repository to get a instance for.
     * @return the repository or null if the repository is invalid.
     */
    public static Repository getRepo(String repo) {
        return CACHE.computeIfAbsent(repo, s -> {
            Repository repository = new Repository(s);
            if (repository.getRepository() == null) return null;// not entirely sure what to do with invalid repos
            return repository;
        });
    }

    private org.eclipse.egit.github.core.Repository getRepository() {
        try {
            return REPOSITORY_SERVICE.getRepository(this.owner, this.name);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        return this.repo.equals(((Repository) o).repo);
    }

    @Override
    public int hashCode() {
        return this.repo.hashCode();
    }
}
