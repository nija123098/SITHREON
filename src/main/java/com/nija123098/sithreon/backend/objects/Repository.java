package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.machines.CheckClient;
import com.nija123098.sithreon.backend.util.ConnectionUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringUtil;
import com.nija123098.sithreon.backend.util.throwable.InvalidRepositoryException;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.connection.GeneralConnectionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * An instance representative of a repository.
 *
 * @author nija123098
 */
public class Repository implements Comparable<Repository> {
    /**
     * A cache of repository objects.
     */
    private static final Map<String, Repository> CACHE = new HashMap<>();

    /**
     * Requires the a repository instance or null if the specified repo does not exist.
     *
     * @param repo the repository to get a instance for.
     * @return the repository or null if the repository is invalid.
     * @throws InvalidRepositoryException if the repository is invalid.
     */
    public static Repository getRepo(String repo) {
        return CACHE.computeIfAbsent(repo, s -> {
            Repository repository = new Repository(s);
            if (!repository.isValid()) throw new InvalidRepositoryException(repo);
            return repository;
        });
    }

    /**
     * The fully qualified path to the repository.
     */
    private final String repo;

    /**
     * Constructs an instance specified by a domain/owner/name representation.
     *
     * @param repo the repo to represent.
     */
    private Repository(String repo) {
        this.repo = repo;
    }

    /**
     * Checks if the repository exists.
     *
     * @return if the repository exists.
     */
    public boolean isValid() {
        try {// Domains with repositories should be general connections
            if (!ConnectionUtil.pageExists(ConnectionUtil.getExternalProtocolName() + "://" + this.repo) || this.getHeadHash() == null)
                return false;// invalid repository
        } catch (GeneralConnectionException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Clones or pulls the repository locally to the location given by {@link this#getLocalRepoLocation()}.
     */
    private void gitClone() {
        boolean pulling = Files.exists(Paths.get(this.getLocalRepoLocation()));
        try {
            if (!pulling) new File(this.getLocalRepoLocation()).mkdirs();
            Process process = new ProcessBuilder("git", pulling ? "pull" : "clone", ConnectionUtil.getExternalProtocolName() + "://" + this.repo).directory(new File(this.getLocalRepoLocation())).start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                ConnectionUtil.throwConnectionException("Timeout " + (pulling ? "pulling" : "cloning") + " git repository " + this.repo);
            }
            process.destroyForcibly();
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to complete git " + (pulling ? "pull" : "clone") + this.repo, e);
        } catch (InterruptedException e) {
            Log.ERROR.log("Unexpected interruption " + (pulling ? "pulling" : "cloning") + " repo " + this.repo, e);
        }
    }

    /**
     * Gets the location of the repository, or where it would be if it were cloned.
     *
     * @return the location of the repository, or where it would be if it were cloned.
     */
    public String getLocalRepoLocation() {
        return "repos/" + this.repo.replace(".", "-");
    }

    /**
     * Gets the {@link RepositoryConfig} for this repository.
     *
     * @return the {@link RepositoryConfig} for this repository.
     */
    public RepositoryConfig getRepositoryConfig() {
        String page = ConnectionUtil.getExternalProtocolName() + "://" + this.repo + "/raw/HEAD/config.cfg";
        if (ConnectionUtil.pageExists(page)) return new RepositoryConfig(StringUtil.readRaw(page));
        return new RepositoryConfig(Collections.emptyList());
    }

    /**
     * Gets the latest HEAD hash of the repository.
     *
     * @return the latest HEAD hash.
     */
    public String getHeadHash() {
        Process process;
        try {
            process = new ProcessBuilder("git", "ls-remote", ConnectionUtil.getExternalProtocolName() + "://" + this.repo, "HEAD").start();
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to connect to git repository", e);
            throw new NoReturnException();
        }
        try {
            process.waitFor(15, TimeUnit.SECONDS);
            if (process.exitValue() != 0)
                ConnectionUtil.throwConnectionException("Unable to connect to git repository " + this.repo);
            byte[] bytes = new byte[40];// SHA-1 length of hex
            if (process.getInputStream().read(bytes) != 40)
                throw new IOException("Could not read all hash bytes of " + this.repo + ".  Received " + Arrays.toString(bytes));
            process.destroy();// shouldn't be necessary
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("IOException connecting to git repository", e);
            throw new NoReturnException();
        } catch (InterruptedException e) {
            if (!Machine.MACHINE.get().closing())
                Log.ERROR.log("Unexpected interruption exception getting HEAD hash of " + this.repo, e);
            process.destroyForcibly();
            return null;// unlikely
        }
    }

    /**
     * Gets the last HEAD hash checked for approval by a {@link CheckClient}.
     *
     * @return the last HEAD hash checked for approval.
     */
    public String getLastCheckedHeadHash() {
        return Database.REPO_LAST_HEAD_HASH.get(this);
    }

    /**
     * Returns if the current hash is the one most recently checked.
     *
     * @return if the current hash is the one most recently checked.
     */
    public boolean isUpToDate() {
        return this.getLastCheckedHeadHash() != null && Objects.equals(this.getLastCheckedHeadHash(), this.getHeadHash());
    }

    /**
     * If the repository is approved for running in matches.
     *
     * @return if the repository is approved for running in matches.
     */
    public boolean isApproved() {
        return Database.REPO_APPROVAL.get(this) && this.isUpToDate();
    }

    /**
     * Gets the priority of the repository for establishing the priority of it's matches.
     *
     * @return the priority of the repository.
     */
    public int getPriority() {
        return Database.PRIORITY_LEVEL.get(this).ordinal();
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

    @Override
    public int compareTo(Repository o) {
        return this.repo.compareTo(o.repo);
    }

    @Override
    public String toString() {
        return this.repo;
    }
}
