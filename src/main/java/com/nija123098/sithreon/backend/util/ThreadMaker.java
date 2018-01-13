package com.nija123098.sithreon.backend.util;

public class ThreadMaker {
    private static final ThreadGroup ALL_THREADS = new ThreadGroup("SITHREON Threads");
    public static final ThreadGroup BACKEND = new ThreadGroup("Backend Threads");
    public static final ThreadGroup NETWORK = new ThreadGroup("Network Threads");
    public static Thread getThread(ThreadGroup group, String name, boolean daemon, Runnable runnable){
        Thread thread = new Thread(group, runnable, name);
        thread.setDaemon(daemon);
        return thread;
    }
}
