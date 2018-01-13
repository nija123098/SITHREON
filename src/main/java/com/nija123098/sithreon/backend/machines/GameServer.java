package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.SocketAcceptor;
import com.nija123098.sithreon.backend.networking.TransferSocket;
import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;

/**
 * The {@link Machine} representation of the server responsible
 * for the running of a game through {@link RunnerClient} instances.
 *
 * @author nija123098
 */
public class GameServer extends Machine {
    private final TransferSocket superServerSocket;
    private final SocketAcceptor socketAcceptor;

    public GameServer() {
        this.socketAcceptor = new SocketAcceptor(this);
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress);
        } catch (IOException e) {
            Log.ERROR.log("Unable to establish connection to super server due to IOException", e);
            throw new RuntimeException();// Won't occur
        }
    }
}
