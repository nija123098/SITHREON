package com.nija123098.sithreon.machine.transfers;

import com.nija123098.sithreon.machine.Machine;
import com.nija123098.sithreon.machine.TransferSocket;

import java.io.Serializable;

/**
 * An object serialized and transferred between .
 *
 * @param <E> the {@link Machine} type an instance is to be received by.
 * @author nija123098
 */
public abstract class Transfer<E extends Machine> implements Serializable {
    private transient TransferSocket receiverSocket;

    /**
     * Sets the transfer socket that this transfer was received by.
     *
     * @param receiverSocket the transfer socket that this transfer was received by.
     */
    public void bind(TransferSocket receiverSocket) {
        this.receiverSocket = receiverSocket;
    }

    /**
     * The machine this instance was received by.
     *
     * @return the machine this instance was received by.
     */
    public E getReceiverMachine() {
        return (E) this.getReceiverSocket().getLocalMachine();
    }

    /**
     * The transfer socket that this instance was received by.
     *
     * @return the transfer socket this instance was received by.
     */
    public TransferSocket getReceiverSocket() {
        return this.receiverSocket;
    }

    /**
     * Called when a transfer is received.
     */
    public abstract void act();
}
