package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.networking.Action;
import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.networking.TransferSocket;
import com.nija123098.sithreon.backend.util.KeepAliveUtil;
import com.nija123098.sithreon.backend.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The representation of a machine on the SITHREON network.
 *
 * @author nija123098
 */
public abstract class Machine {
    /**
     * The latest machine to have been registered.
     * Accessing this should be a last resort.
     */
    public static final AtomicReference<Machine> MACHINE = new AtomicReference<>();

    /**
     * The sockets that connect this machine to the rest of the network.
     */
    private final Set<TransferSocket> sockets = new HashSet<>();

    /**
     * Boolean states for closing.
     */
    private final AtomicBoolean closed = new AtomicBoolean(), closing = new AtomicBoolean();

    /**
     * The {@link Runnable}s to invoke on startup.
     */
    private final List<Runnable> onClose = new ArrayList<>(1);

    /**
     * Constructs a machine with a keep alive thread.
     */
    protected Machine() {
        KeepAliveUtil.start();
        if (MACHINE.get() != null)
            Log.WARN.log("Launching a second Machine instance in the same program instance, this may cause unexpected issues.");
        MACHINE.set(this);
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
        KeepAliveUtil.stop();
    }

    /**
     * Adds a socket to the {@link Set} of {@link TransferSocket}s
     * that have connections between this and other machines.
     *
     * @param socket the socket to register.
     */
    public void registerSocket(TransferSocket socket) {
        this.sockets.add(socket);
    }

    /**
     * Removes the socket from the registered sockets.
     *
     * @param socket the socket to deregister.
     */
    public void deregisterSocket(TransferSocket socket) {
        this.sockets.remove(socket);
    }

    /**
     * Commands all connected machines to close, then closes it's self.
     */
    @Action(MachineAction.CLOSE_ALL)
    public void closeAll() {
        this.sockets.forEach(socket -> socket.write(MachineAction.CLOSE_ALL));
        this.close();
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
