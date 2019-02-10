package com.nija123098.sithreon.backend.util;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Provides general utilities for processes.
 */
public class ProcessUtil {
    /**
     * Waits for a process to terminate for provided time.
     * <p>
     * If it terminates before that time it exits immediately with {@code true},
     * otherwise exits after the specified amount of time with {@code false}
     * having called {@link Process#destroyForcibly()} to ensure termination.
     *
     * @param milliseconds the time to allow to pass waiting for the process to end.
     * @param process      the process to wait for to end, and if necessary destroy.
     * @return if it was necessary to destroy the process.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public static boolean waitOrDestroy(long milliseconds, Process process) throws InterruptedException {
        if (!process.waitFor(milliseconds, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return true;
        }
        return false;
    }

    /**
     * Checks a process' exit code, and runs the provided {@link ExitConsumer} if the code is not 0.
     *
     * @param process  the process to check the exit code of.
     * @param runnable the lambda to run in case of the process not exiting with 0.
     * @return the exit code of the process.
     * @throws IOException the IO exception if occured in the provided {@link ExitConsumer}.
     */
    public static int runNonZero(Process process, ExitConsumer runnable) throws IOException {
        int exitCode = process.exitValue();
        if (exitCode == 0) return 0;
        throw runnable.accept(exitCode);
    }

    @FunctionalInterface
    public interface ExitConsumer {
        RuntimeException accept(int exitCode) throws IOException;
    }
}
