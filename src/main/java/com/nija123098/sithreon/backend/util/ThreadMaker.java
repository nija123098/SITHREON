package com.nija123098.sithreon.backend.util;

/**
 * A utility for making and organizing {@link Thread}s.
 *
 * @author nija123098
 */
public class ThreadMaker {
    /**
     * The thread group for all threads directly spawned by the project.
     */
    private static final ThreadGroup ALL_THREADS = new ThreadGroup("SITHREON Threads");

    /**
     * The thread group for all backend threads.
     */
    public static final ThreadGroup BACKEND = new ThreadGroup(ALL_THREADS, "Backend Threads");

    /**
     * The thread group for networking threads.
     */
    public static final ThreadGroup NETWORK = new ThreadGroup(ALL_THREADS, "Network Threads");

    /**
     * A utility for making and organizing threads.
     *
     * @param group the {@link ThreadGroup} the thread should belong to.
     * @param name the name of the thread to make.
     * @param daemon if the thread should be a demon thread.
     * @param runnable the runnable to execute when the thread is started.
     * @return the thread made according to the parameters.
     */
    public static Thread getThread(ThreadGroup group, String name, boolean daemon, Runnable runnable){
        Thread thread = new Thread(group, runnable, name);
        thread.setDaemon(daemon);
        return thread;
    }
}
