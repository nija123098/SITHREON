package com.nija123098.sithreon.backend.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simple utility to keep the program alive.
 *
 * @author nija123098
 */
public class KeepAliveUtil {
    private static final AtomicInteger LIVE_REQUESTS = new AtomicInteger();
    private static final AtomicReference<Thread> KEEP_ALIVE_THREAD = new AtomicReference<>();

    public static void start() {
        if (LIVE_REQUESTS.getAndIncrement() == 0) {
            KEEP_ALIVE_THREAD.set(ThreadMaker.getThread(ThreadMaker.BACKEND, "Keep Alive Thread", false, () -> {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ignored) {
                    Log.INFO.log("Closing keep alive thread");
                }
            }));
            KEEP_ALIVE_THREAD.get().start();
        }
    }

    public static void stop() {
        if (LIVE_REQUESTS.decrementAndGet() == 0) KEEP_ALIVE_THREAD.getAndSet(null).interrupt();
    }
}
