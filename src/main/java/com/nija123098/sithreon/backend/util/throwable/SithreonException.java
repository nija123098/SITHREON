package com.nija123098.sithreon.backend.util.throwable;

/**
 * The parent exception for all exceptions thrown by the project.
 */
public class SithreonException extends RuntimeException {
    public SithreonException(String message) {
        super(message);
    }

    public SithreonException(String message, Throwable cause) {
        super(message, cause);
    }
}
