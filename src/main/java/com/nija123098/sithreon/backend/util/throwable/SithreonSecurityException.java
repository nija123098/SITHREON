package com.nija123098.sithreon.backend.util.throwable;

public class SithreonSecurityException extends SithreonException {
    public SithreonSecurityException(String message) {
        super(message);
    }

    public SithreonSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
