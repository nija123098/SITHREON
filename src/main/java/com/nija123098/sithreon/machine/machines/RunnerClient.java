package com.nija123098.sithreon.machine.machines;

import com.nija123098.sithreon.Config;
import com.nija123098.sithreon.machine.Machine;
import com.nija123098.sithreon.machine.TransferSocket;
import com.nija123098.sithreon.util.Log;

import java.io.IOException;

/**
 * The {@link Machine} representation for running a competitor's code
 * which communicates with a {@link GameServer}.  The other
 * competitor's code is running on a parallel runner instance.
 *
 * @author nija123098
 */
public class RunnerClient extends Machine {
    private final TransferSocket superServerSocket;

    public RunnerClient() {
        try {
            this.superServerSocket = new TransferSocket(this, Config.gameServerAddress);
        } catch (IOException e) {
            Log.ERROR.log("Unable to establish connection to game server due to IOException", e);
            throw new RuntimeException();// Won't occur
        }
    }
}
