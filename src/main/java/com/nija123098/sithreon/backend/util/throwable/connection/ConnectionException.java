package com.nija123098.sithreon.backend.util.throwable.connection;

import com.nija123098.sithreon.backend.util.throwable.SithreonException;

/**
 * Exception for when a connection has an unexpected state or fails.
 */
public class ConnectionException extends SithreonException {

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(String message) {
        super(message);
    }
}
