package com.nija123098.sithreon.backend.util;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ProcessUtilTest {// works on *nix

    private static final String COUNT_OPTION = System.getProperty("os.name").contains("Win") ? "-n" : "-c";

    @Test
    public void waitOrDestroy() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ping", COUNT_OPTION, "1", "localhost").start();
        assertFalse(ProcessUtil.waitOrDestroy(1000, process));

        process = new ProcessBuilder("ping", COUNT_OPTION, "100", "localhost").start();
        assertTrue(ProcessUtil.waitOrDestroy(5000, process));

        process = new ProcessBuilder("git", "red").start();// not intended to work
        assertFalse(ProcessUtil.waitOrDestroy(5000, process));
    }

    @Test// waits are used to ensure exit code is available
    public void runNonZero() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ping", COUNT_OPTION, "1", "localhost").start();
        assertFalse(ProcessUtil.waitOrDestroy(1000, process));
        assertEquals(0, ProcessUtil.throwNonZero(process, exitCode -> fail()));

        process = new ProcessBuilder("git", "red").start();// not intended to work
        assertFalse(ProcessUtil.waitOrDestroy(1000, process));
        assertEquals(1, ProcessUtil.throwNonZero(process, exitCode -> assertEquals(1, exitCode)));
    }
}
