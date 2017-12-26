package com.nija123098.sithreon.machine.machines;

import com.nija123098.sithreon.machine.Machine;
import com.nija123098.sithreon.machine.SocketAcceptor;
import com.nija123098.sithreon.util.Database;

/**
 * The {@link Machine} representation of the main coordination server.
 * It is responsible for coordinating the checking and running of
 * competitor's code in order to order the competitor's AIs.
 *
 * @author nija123098
 */
public class SuperServer extends Machine {
    private final SocketAcceptor socketAcceptor;

    public SuperServer() {
        Database.init();
        this.socketAcceptor = new SocketAcceptor(this);
    }
}
