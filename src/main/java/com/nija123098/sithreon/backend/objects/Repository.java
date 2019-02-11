package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.machines.CheckClient;
import com.nija123098.sithreon.backend.util.*;
import com.nija123098.sithreon.backend.util.throwable.IOExceptionWrapper;
import com.nija123098.sithreon.backend.util.throwable.InvalidRepositoryException;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;
import com.nija123098.sithreon.backend.util.throwable.connection.ConnectionException;
import com.nija123098.sithreon.backend.util.throwable.connection.SpecificConnectionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * The max time it should take to fetch the repository.
     */
    private static final Integer FETCH_TIME = 120_000;

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
            try {
                if (Config.checkRepositoryValidity && !repository.isValid()) throw new InvalidRepositoryException(repo);
            } catch (ConnectionException e) {
                throw new InvalidRepositoryException(repo);
            }
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
        this.repo = repo.trim();
    }

    /**
     * Checks if the repository exists.
     *
     * @return if the repository exists.
     */
    public boolean isValid() {
        if (this.repo.startsWith("/")) return Files.exists(Paths.get(this.repo, ".git"));
        try {// Domains with repositories should be general connections
            if (!ConnectionUtil.pageExists(this.repo) || this.getHeadHash() == null)
                return false;// invalid repository
        } catch (SpecificConnectionException e) {
            e.printStackTrace();
            return false;
        } catch (ConnectionException e) {
            throw e;
        } catch (Exception e) {
            Log.WARN.log("Exception checking if repository is valid: " + this.repo, e);
            return false;
        }
        return true;
    }

    /**
     * Fetches the repository locally to the location given
     * by {@link Repository#getLocalRepoLocation()} if necessary.
     *
     * @param hash the hash to set the repository at.
     */
    public synchronized void getSource(String hash) {
        File location = new File(this.getLocalRepoLocation());
        if (location.exists()) {
            if (this.localVersion().equals(hash)) return;
            try {
                FileUtil.deleteFiles(location.toPath());
            } catch (IOException e) {
                Log.ERROR.log("IOException deleting files for branch: " + location.getAbsolutePath(), e);
            }
        }
        location.mkdirs();
        try {
            Process initProcess = new ProcessBuilder("git", "init", "--quiet").directory(location).start();
            if (ProcessUtil.waitOrDestroy(1_000, initProcess))
                ConnectionUtil.throwConnectionException("Timeout for git init git at: " + location.getAbsolutePath());
            ProcessUtil.runNonZero(initProcess, exitCode -> new SithreonException("Unable to init git repo: " + exitCode));
        } catch (IOException e) {
            Log.ERROR.log("IOException running git init", e);
        } catch (InterruptedException e) {
            Log.ERROR.log("Unexpected interruption running git init", e);
        }
        try {
            Process fetchProcess = new ProcessBuilder("git", "fetch", "--quiet", "--depth", "1", this.repo, hash).directory(location).start();
            if (ProcessUtil.waitOrDestroy(FETCH_TIME, fetchProcess))
                ConnectionUtil.throwConnectionException("Timeout for git fetch " + this.repo + " " + hash + ": " + StreamUtil.readFully(fetchProcess.getErrorStream(), 1024));
            ProcessUtil.runNonZero(fetchProcess, exitCode -> new SithreonException("Unable to fetch for repository git repo: " + this.repo + " " + hash + ": " + exitCode + " " + StreamUtil.readFully(fetchProcess.getErrorStream())));
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to complete git fetch: " + this.repo + " " + hash, e);
        } catch (InterruptedException e) {
            Log.ERROR.log("Unexpected interruption running git fetch", e);
        }
        try {
            Process checkoutProcess = new ProcessBuilder("git", "checkout", "--quiet", hash).directory(location).start();
            if (ProcessUtil.waitOrDestroy(FETCH_TIME, checkoutProcess))
                ConnectionUtil.throwConnectionException("Timeout for git checkout " + this.repo + " " + hash + ": " + StreamUtil.readFully(checkoutProcess.getErrorStream(), 1024));
            ProcessUtil.runNonZero(checkoutProcess, exitCode -> new SithreonException("Unable to checkout for repository git repo: " + this.repo + " " + hash + ": " + exitCode + " " + StreamUtil.readFully(checkoutProcess.getErrorStream())));
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to complete git checkout: " + this.repo + " " + hash, e);
        } catch (InterruptedException e) {
            Log.ERROR.log("Unexpected interruption running git fetch", e);
        }
    }

    private String localVersion() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--verify", "--quiet", "HEAD").start();
            if (ProcessUtil.waitOrDestroy(10_000, process)) {
                throw new SithreonException("git rev-parse took too long");
            }
            byte[] current = new byte[40];
            process.getInputStream().read(current);
            process.destroyForcibly();
            return new String(current, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to check git version: " + this.repo);
        } catch (InterruptedException e) {
            Log.ERROR.log("Unexpected interrupt running git checkout", e);
        }
        throw new NoReturnException();
    }

    /**
     * Gets the location of the repository, or where it would be if it were cloned.
     *
     * @return the location of the repository, or where it would be if it were cloned.
     */
    public String getLocalRepoLocation() {
        return Config.repositoryFolder + File.separator + this.repo.replace('/', File.separatorChar);
    }

    /**
     * Gets the {@link RepositoryConfig} for this repository.
     *
     * @return the {@link RepositoryConfig} for this repository.
     */
    public RepositoryConfig getRepositoryConfig() {
        String page = this.repo + "/raw/HEAD/config.cfg";
        if (this.repo.startsWith("/")) {
            Path path = Paths.get(page);
            if (Files.exists(path)) {
                try {
                    return new RepositoryConfig(Files.readAllLines(path));
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            }
        } else if (ConnectionUtil.pageExists(page)) return new RepositoryConfig(StringUtil.readRaw(page));
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
            process = new ProcessBuilder("git", "ls-remote", this.repo, "HEAD").start();
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
