package com.nija123098.sithreon.backend.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple utility to keep the program alive.
 *
 * @author nija123098
 */
public class KeepAliveUtil {
    private static final Object LOCK = new Object();
    private static final Set<Object> LIVING_OBJECTS = ConcurrentHashMap.newKeySet();

    public static void start(Object living) {
        if (LIVING_OBJECTS.isEmpty()) {
            ThreadMaker.getThread(ThreadMaker.BACKEND, "Keep Alive Thread", false, () -> {
                synchronized (LOCK) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        Log.WARN.log("Unexpected interrupt of Keep Alive Thread", e);
                    }
                }
            }).start();
        }
        LIVING_OBJECTS.add(living);
    }

    public static void stop(Object dieing) {
        LIVING_OBJECTS.remove(dieing);
        if (LIVING_OBJECTS.isEmpty()) synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }
}
