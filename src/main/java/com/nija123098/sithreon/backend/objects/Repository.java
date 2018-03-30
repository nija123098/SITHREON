package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Database;
import com.nija123098.sithreon.backend.machines.CodeCheckClient;
import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * An instance representative of a Github repository.
 *
 * @author nija123098
 */
public class Repository implements Comparable<Repository>{
    /**
     * A cache of repository objects.
     */
    private static final Map<String, Repository> CACHE = new HashMap<>();
    /**
     * The identifications for the repository.
     */
    private String repo;

    /**
     * Constructs an instance specified a owner/name representation.
     *
     * @param repo the repo to represent.
     */
    private Repository(String repo) {
        this.repo = repo;
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
            if (repository.getHeadHash() == null) throw new RuntimeException("Invalid repository: " + repo);
            return repository;
        });
    }

    /**
     * Gets the latest HEAD hash of the repository.
     *
     * @return the latest HEAD hash.
     */
    public String getHeadHash(){
        try {
            Process process = new ProcessBuilder("git", "ls-remote", "git://github.com/" + this.repo + ".git").start();
            process.waitFor(15, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            if (exitCode != 0) return null;// probably invalid
            byte[] bytes = new byte[40];
            process.getInputStream().read(bytes);
            process.destroy();// shouldn't be necessary
            return new String(bytes, Charset.forName("UTF-8"));
        } catch (IOException | InterruptedException e) {
            Log.WARN.log("Exception getting HEAD hash process", e);
        } catch (IllegalThreadStateException e) {
            Log.WARN.log("HEAD get process timed out", e);
        }
        return null;
    }

    /**
     * Gets the last HEAD hash checked for approval by a {@link CodeCheckClient}.
     *
     * @return the last HEAD hash checked for approval.
     */
    public String getLastCheckedHeadHash(){
        return Database.REPO_LAST_HEAD_HASH.get(this);
    }

    /**
     * Returns if the current hash is
     *
     * @return if the
     */
    public boolean isUpToDate(){
        return this.getLastCheckedHeadHash() != null && Objects.equals(this.getLastCheckedHeadHash(), this.getHeadHash());
    }

    public boolean isApproved(){
        return Database.REPO_APPROVAL.get(this) && this.isUpToDate();
    }

    public int getPriority(){
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
