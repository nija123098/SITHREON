package com.nija123098.sithreon.machine.machines;

import com.nija123098.sithreon.Config;
import com.nija123098.sithreon.machine.Machine;
import com.nija123098.sithreon.machine.TransferSocket;
import com.nija123098.sithreon.util.Log;

import java.io.IOException;

/**
 * The {@link Machine} representation of a client to the super
 * server that checks the content of a repository to determine if
 * it is possible and safe to run by the {@link RunnerClient}.
 *
 * @author nija123098
 */
public class CodeCheckClient extends Machine {
    private final TransferSocket superServerSocket;

    public CodeCheckClient() {
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress);
        } catch (IOException e) {
            Log.ERROR.log("Unable to establish connection to super server due to IOException", e);
            throw new RuntimeException();// Won't occur
        }
    }
}
