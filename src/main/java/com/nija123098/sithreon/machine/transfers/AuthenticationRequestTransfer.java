package com.nija123098.sithreon.machine.transfers;

import com.nija123098.sithreon.machine.Machine;

/**
 * The {@link Transfer} which starts authentication.
 * As a response a {@link AuthenticationTransfer} will be sent
 * back with a hash formed partially from the hashRequest.
 *
 * @author nija123098
 */
public class AuthenticationRequestTransfer extends Transfer<Machine> {
    private final byte[] hashRequest;
    private final long time;

    /**
     * Constructs an authentication request with the specified time and hash request.
     *
     * @param hashRequest the bytes to hash with the time and key.
     * @param time        the time to hash with the hash request and time.
     */
    public AuthenticationRequestTransfer(byte[] hashRequest, long time) {
        this.hashRequest = hashRequest;
        this.time = time;
    }

    /**
     * Makes the socket respond with an authentication transfer
     * to validate that this server is authentic.
     */
    @Override
    public void act() {
        this.getReceiverSocket().write(new AuthenticationTransfer(this.hashRequest, this.time));
    }
}
