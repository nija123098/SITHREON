package com.nija123098.sithreon.backend.util.throwable;

import com.nija123098.sithreon.backend.util.Log;

/**
 * Exception for compile compliance.  This should never be thrown.
 */
public class NoReturnException extends SithreonException {
    public NoReturnException() {
        super("No return exception reached!");
        Log.ERROR.log("No return exception reached!  This should no occur");
    }
}
