package com.nija123098.sithreon.backend.util.throwable;

import java.io.IOException;

/**
 * Wraps an {@link IOException} in a {@link RuntimeException}.
 *
 * This does not extend {@link SithreonException} so it does not get accidentally caught.
 */
public class IOExceptionWrapper extends RuntimeException {
    public IOExceptionWrapper(IOException e) {
        super(e);
    }

    @Override
    public synchronized IOException getCause() {
        return (IOException) super.getCause();
    }
}
