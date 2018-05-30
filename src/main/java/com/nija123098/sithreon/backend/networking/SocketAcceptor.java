package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.ThreadMaker;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The wrapper for a {@link ServerSocket} which accepts all incoming sockets
 * and wraps those in {@link TransferSocket}s which handle authentication.
 *
 * @author nija123098
 */
public class SocketAcceptor {
    private final ServerSocket serverSocket;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<TransferSocket> sockets = new HashSet<>();

    /**
     * Constructs a socket acceptor.
     *
     * @param machine the {@link Machine} which this instance operates for.
     */
    public SocketAcceptor(Machine machine, Integer port) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            Log.ERROR.log("Unable to make server socket", e);
        }
        this.serverSocket = serverSocket;
        ThreadMaker.getThread(ThreadMaker.NETWORK, "Socket Acceptor Thread", true, () -> {
            while (!this.closed.get()) {
                try {
                    Socket socket = this.serverSocket.accept();// will not throw an NPE
                    if (this.closed.get()) return;// Ensure that this socket is not accepted while this is closed
                    this.sockets.add(new TransferSocket(machine, socket));
                } catch (IOException e) {
                    Log.WARN.log("IOException accepting socket", e);
                }
            }
        }).start();
        machine.runOnClose(this::close);
        Log.DEBUG.log("Started socket acceptor");
    }

    /**
     * Closes this acceptor and it's accepting thread.
     */
    private void close() {
        if (this.closed.get()) return;
        this.closed.set(true);
        this.sockets.forEach(TransferSocket::close);
    }
}
