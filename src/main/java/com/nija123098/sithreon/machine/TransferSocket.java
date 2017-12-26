package com.nija123098.sithreon.machine;

import com.nija123098.sithreon.Config;
import com.nija123098.sithreon.machine.machines.SuperServer;
import com.nija123098.sithreon.machine.transfers.AuthenticationRequestTransfer;
import com.nija123098.sithreon.machine.transfers.AuthenticationTransfer;
import com.nija123098.sithreon.machine.transfers.Transfer;
import com.nija123098.sithreon.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper for a {@link Socket} which manages
 * sending end to end encrypted {@link Transfer}s.
 *
 * @author nija123098
 */
public class TransferSocket {
    private static final Random RANDOM = new Random();
    private final byte[] hashRequest = new byte[256];
    private final long authenticationTime = System.currentTimeMillis();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;
    private final Machine localMachine;

    /**
     * Constructs a client socket instance for connecting to a server.
     *
     * @param localMachine the machine this instance belongs to.
     * @param host         the host address of the {@link Machine} to connect to.
     * @throws IOException if the {@link Socket} constructor throws an {@link IOException}.
     */
    public TransferSocket(Machine localMachine, String host) throws IOException {
        this(localMachine, new Socket(host, Config.port));
    }

    /**
     * Constructs a wrapper for a socket that has been accepted by a server.
     *
     * @param localMachine the {@link Machine} this instance belongs to.
     * @param socket       the accepted socket from a {@link SocketAcceptor}.
     * @throws IOException if flushing or a stream throws an {@link IOException}.
     */
    TransferSocket(Machine localMachine, Socket socket) throws IOException {
        this.localMachine = localMachine;
        this.socket = socket;
        this.outputStream = new ObjectOutputStream(this.socket.getOutputStream());
        this.outputStream.flush();
        this.inputStream = new ObjectInputStream(this.socket.getInputStream());
        Thread readThread = new Thread(() -> {
            boolean authenticated = false;
            while (!this.closed.get()) {
                try {
                    Transfer transfer = ((WrapperObject) this.inputStream.readObject()).getObject();
                    Log.TRACE.log("Handling incoming transfer of type " + transfer.getClass().getSimpleName());
                    if (!authenticated && !(transfer instanceof AuthenticationRequestTransfer)) {
                        if (((AuthenticationTransfer) transfer).isAuthentic(this.hashRequest, this.authenticationTime)) {
                            authenticated = true;
                            Log.INFO.log("Authenticated socket from " + this.socket.getRemoteSocketAddress());
                        } else
                            Log.ERROR.log("Inauthentic server attempted connecting " + this.socket.getRemoteSocketAddress());
                    } else {
                        transfer.bind(this);
                        transfer.act();
                    }
                } catch (EOFException e) {
                    Log.INFO.log("Opposite side closed " + this.socket.getRemoteSocketAddress());
                    this.close();
                } catch (SocketException e) {
                    if (e.getMessage().equals("Connection reset")) {
                        Log.INFO.log("Opposite side closed " + this.socket.getRemoteSocketAddress());
                        this.close();
                    } else Log.WARN.log("SocketException receiving object", e);
                } catch (IOException e) {
                    Log.WARN.log("IOException receiving object", e);
                } catch (ClassNotFoundException | ClassCastException | SecurityException e) {
                    Log.ERROR.log("Improper read from " + this.socket.getRemoteSocketAddress(), e);
                }
            }
        }, "TransferSocketReadThread" + this.socket.getRemoteSocketAddress());
        readThread.setDaemon(true);
        readThread.start();
        RANDOM.nextBytes(this.hashRequest);
        this.write(new AuthenticationRequestTransfer(this.hashRequest, this.authenticationTime));
        this.localMachine.runOnClose(this::close);
    }

    /**
     * Gets the {@link Machine} this instance belongs to.
     *
     * @return the {@link Machine} this instance belongs to.
     */
    public Machine getLocalMachine() {
        return this.localMachine;
    }

    /**
     * Writes a {@link Transfer} to the stream.
     *
     * @param transfer the instance to write to the stream.
     */
    public synchronized void write(Transfer transfer) {
        try {
            this.outputStream.writeObject(new WrapperObject(transfer));
            this.outputStream.flush();
            Log.TRACE.log("Flushed transfer of type " + transfer.getClass().getSimpleName());
        } catch (IOException e) {
            Log.WARN.log("IOException flushing object object of type " + transfer.getClass().getName(), e);
        }
    }

    /**
     * Closes the wrapped socket.
     */
    public void close() {
        if (this.closed.get()) return;
        this.closed.set(true);
        try {
            this.inputStream.close();
            this.outputStream.close();
            this.socket.close();
        } catch (IOException e) {
            Log.ERROR.log("IOException closing socket to " + this.socket.getRemoteSocketAddress(), e);
        }
        Log.INFO.log("Closed socket to " + this.socket.getRemoteSocketAddress());
        if (!(this.getLocalMachine() instanceof SuperServer)) {
            this.getLocalMachine().close();
            Log.INFO.log("Closing machine due to losing connection to the super server");
        }
    }
}
