package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.networking.Action;
import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.networking.ManagedMachineType;
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
        KeepAliveUtil.start(this);
        if (MACHINE.get() != null) {
            Log.WARN.log("Launching a second Machine instance in the same program instance, this may cause unexpected issues.");
        } else MACHINE.set(this);
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
        KeepAliveUtil.stop(this);
    }

    /**
     * If this instance is closing.
     */
    public boolean closing() {
        return this.closing.get();
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
     * The {@link MachineAction} method for indicating that a {@link MachineAction} is ready to server.
     *
     * @param machineType the self reported {@link ManagedMachineType} reporting to be ready to serve.
     * @param socket      the socket representing the {@link ManagedMachineType} reporting.
     */
    @Action(MachineAction.READY_TO_SERVE)
    public final void readyToServe(ManagedMachineType machineType, TransferSocket socket) {
        this.notifyReady(machineType, socket);
    }

    /**
     * The {@link Machine#readyToServe(ManagedMachineType, TransferSocket)} method that can be
     * overridden to indicate that the machine can make use of a {@link Machine} reporting to be ready to serve.
     *
     * @param machineType the self reported {@link ManagedMachineType} reporting to be ready to serve.
     * @param socket      the socket representing the {@link ManagedMachineType} reporting.
     */
    protected void notifyReady(ManagedMachineType machineType, TransferSocket socket) {
        Log.WARN.log("Managed machine of type " + machineType + " from " + socket.getConnectionName() + " reported ready, closing connection");
        socket.close();
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
