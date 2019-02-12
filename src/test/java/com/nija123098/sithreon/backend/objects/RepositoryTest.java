package com.nija123098.sithreon.backend.objects;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.FileUtil;
import com.nija123098.sithreon.backend.util.ProcessUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class RepositoryTest {

    private static final String TEMP = (FileUtil.getTemporaryDirectory() + "testing/repository-test/").replace("/", File.separator);
    private static final String REPO1 = TEMP + "repo1";
    private static final String REPO2 = TEMP + "repo2";
    private static final String REPO3 = TEMP + "repo3";// doesn't exist

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        Config.checkRepositoryValidity = false;
        Config.machineId = "test-machine";
        new File(REPO1).mkdirs();
        new File(REPO2).mkdirs();
        new File(REPO3).mkdirs();
        if (ProcessUtil.waitOrDestroy(2000, new ProcessBuilder("git", "init").directory(new File(REPO1)).start()))
            fail();
        if (ProcessUtil.waitOrDestroy(2000, new ProcessBuilder("git", "init").directory(new File(REPO2)).start()))
            fail();
    }

    @Test
    public void getRepo() {
        Repository repository = Repository.getRepo(REPO1);
        assertSame(repository, Repository.getRepo(REPO1));
        repository = Repository.getRepo(REPO2);
        assertSame(repository, Repository.getRepo(REPO2));
        repository = Repository.getRepo(REPO3);
        assertSame(repository, Repository.getRepo(REPO3));
    }

    @Test
    public void isValid() {
        assertTrue(Repository.getRepo(REPO1).isValid());
        assertTrue(Repository.getRepo(REPO2).isValid());
        assertFalse(Repository.getRepo(REPO3).isValid());
    }

    @Test
    public void getLocalRepoLocation() {
        assertEquals(Config.repositoryDirectory + REPO1, Repository.getRepo(REPO1).getLocalRepoLocation());
        assertEquals(Config.repositoryDirectory + REPO3, Repository.getRepo(REPO3).getLocalRepoLocation());
    }

    @Test
    public void compareTo() {
        Repository repo1 = Repository.getRepo(REPO1);
        Repository repo2 = Repository.getRepo(REPO2);
        Repository repo3 = Repository.getRepo(REPO3);
        assertEquals(0, repo1.compareTo(repo1));
        assertTrue(repo1.compareTo(repo2) < 0);
        assertTrue(repo1.compareTo(repo3) < 0);

        assertTrue(repo2.compareTo(repo1) > 0);
        assertEquals(0, repo2.compareTo(repo2));
        assertTrue(repo2.compareTo(repo3) < 0);

        assertTrue(repo3.compareTo(repo1) > 0);
        assertTrue(repo3.compareTo(repo2) > 0);
        assertEquals(0, repo3.compareTo(repo3));
    }

    @AfterClass
    public static void tearDown() throws IOException {
        FileUtil.deleteFiles(Paths.get(TEMP));
    }
}
