package com.nija123098.sithreon.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The representation of a machine on the SITHREON network.
 *
 * @author nija123098
 */
public abstract class Machine {
    private final AtomicBoolean closed = new AtomicBoolean(), closing = new AtomicBoolean();
    private final List<Runnable> onClose = new ArrayList<>(1);
    private final Thread keepAliveThread = new Thread(() -> {
        while (!this.closed.get()) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
            }
        }
    }, "KeepAliveThread");

    /**
     * Constructs a machine with a keep alive thread.
     */
    protected Machine() {
        this.keepAliveThread.start();
    }

    /**
     * Closes the keep alive thread and runs all registered
     * {@link Runnable}s to be run on machine shutdown.
     */
    public void close() {
        if (this.closing.get()) return;
        this.closing.set(true);
        this.onClose.forEach(Runnable::run);
        this.closed.set(true);
        this.keepAliveThread.interrupt();
    }

    /**
     * Registers a runnable to be run when the machine is to be closed.
     *
     * @param onClose the runnable to register.
     */
    public void runOnClose(Runnable onClose) {
        this.onClose.add(onClose);
    }
}
