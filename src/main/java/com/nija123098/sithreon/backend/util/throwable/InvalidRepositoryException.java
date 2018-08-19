package com.nija123098.sithreon.backend.util.throwable;

public class InvalidRepositoryException extends SithreonException {
    /**
     * The repository that is invalid.
     */
    private final String repository;

    /**
     * Builds an exception for invalid repository.
     *
     * @param repository the invalid repository.
     */
    public InvalidRepositoryException(String repository) {
        super("Invalid repository: " + repository);
        this.repository = repository;
    }

    /**
     * Gets the invalid repository.
     *
     * @return the full invalid repository name.
     */
    public String getRepository() {
        return repository;
    }
}
