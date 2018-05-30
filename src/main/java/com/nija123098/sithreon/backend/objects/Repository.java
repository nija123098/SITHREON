package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.machines.CodeCheckClient;
import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * An instance representative of a Github repository.
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
     */
    public static Repository getRepo(String repo) {
        return CACHE.computeIfAbsent(repo, s -> {
            Repository repository = new Repository(s);
            if (repository.getHeadHash() == null) throw new RuntimeException("Invalid repository: " + repo);
            return repository;
        });
    }

    /**
     * The identifications for the repository.
     */
    private final String repo;

    /**
     * Constructs an instance specified a owner/name representation.
     *
     * @param repo the repo to represent.
     */
    private Repository(String repo) {
        this.repo = repo;
    }

    /**
     * Gets the latest HEAD hash of the repository.
     *
     * @return the latest HEAD hash.
     */
    public String getHeadHash() {
        Process process;
        try {
            process = new ProcessBuilder("git", "ls-remote", "git://github.com/" + this.repo + ".git", "HEAD").start();
        } catch (IOException e) {
            Log.ERROR.log("Unable to connect to Github", e);
            return null;// won't occur
        }
        try {
            process.waitFor(15, TimeUnit.SECONDS);
            if (process.exitValue() != 0) Log.ERROR.log("Unable to connect to Github");
            byte[] bytes = new byte[40];// SHA-1 length of hex
            if (process.getInputStream().read(bytes) != 40) throw new IOException("Could not read all hash bytes");
            process.destroy();// shouldn't be necessary
            return new String(bytes, Charset.forName("UTF-8"));
        } catch (IOException e) {
            Log.ERROR.log("Exception getting HEAD hash process", e);
        } catch (InterruptedException e) {
            if (!Machine.MACHINE.get().closing()) Log.ERROR.log("Exception getting HEAD hash process", e);
            else process.destroyForcibly();
        } catch (IllegalThreadStateException e) {
            Log.ERROR.log("HEAD get process timed out", e);
        }
        return null;
    }

    /**
     * Gets the last HEAD hash checked for approval by a {@link CodeCheckClient}.
     *
     * @return the last HEAD hash checked for approval.
     */
    public String getLastCheckedHeadHash() {
        return Database.REPO_LAST_HEAD_HASH.get(this);
    }

    /**
     * Returns if the current hash is
     *
     * @return if the
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
