package com.nija123098.sithreon.backend.util.throwable.connection;

import com.nija123098.sithreon.backend.util.ConnectionHelper;

/**
 * Used when general connectivity is unavailable,
 * such as a major provider being unavailable,
 * or no internet connection is present.
 * <p>
 * Defined by {@link ConnectionHelper#hasGeneralConnection()}.
 */
public class GeneralConnectionException extends ConnectionException {
    public GeneralConnectionException(String message) {
        super("Unable to connect a general connection domain.  Specific message: " + message);
    }

    public GeneralConnectionException(String message, Throwable cause) {
        super("Unable to connect a general connection domain.  Specific message: " + message, cause);
    }
}
