package com.nija123098.sithreon.backend.util.throwable.connection;

import com.nija123098.sithreon.backend.util.ConnectionHelper;

/**
 * Used when a connection fails due to the fail to response or unexpected state
 * but general connectivity is still available, defined by {@link ConnectionHelper#hasGeneralConnection()}.
 */
public class SpecificConnectionException extends ConnectionException {

    public SpecificConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpecificConnectionException(String message) {
        super(message);
    }
}
