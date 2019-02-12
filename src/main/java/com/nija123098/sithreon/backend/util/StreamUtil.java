package com.nija123098.sithreon.backend.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Provides utilities for easy use of streams.
 */
public class StreamUtil {
    /**
     * Reads the provided {@link InputStream} as a UTF-8 string for all that is currently stored in it.
     *
     * @param stream     the stream to read.
     * @param bufferSize the buffer size.
     * @return the string provided from the stream.
     * @throws IOException if the stream can not be read from.
     */
    public static String readFully(InputStream stream, int bufferSize) throws IOException {
        ByteHandler bytes = new ByteHandler();
        int size;
        byte[] buffer = new byte[bufferSize];
        while ((size = stream.read(buffer)) != -1) {
            bytes.add(buffer, size);
        }
        return new String(bytes.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Reads the provided {@link InputStream} as a UTF-8 string for all that is currently stored in it.
     *
     * @param stream the stream to read.
     * @return the string provided from the stream.
     * @throws IOException if the stream can not be read from.
     */
    public static String readFully(InputStream stream) throws IOException {
        return readFully(stream, 4096);
    }

    /**
     * Reads from a {@link InputStream} and writes the content to the provided {@link OutputStream}.
     *
     * @param input      the {@link InputStream} to read from.
     * @param output     the {@link OutputStream} to write to.
     * @param bufferSize the buffer size.
     * @param threadName the name of the thread to run.
     * @return the running {@link Thread} being used to read and write to the streams.
     */
    public static Thread readConstantly(InputStream input, OutputStream output, int bufferSize, String threadName) {
        Thread thread = new Thread(() -> {
            int size;
            byte[] buffer = new byte[bufferSize];
            try {
                while ((size = input.read(buffer)) != -1) {
                    output.write(buffer, 0, size);
                    output.flush();
                }
                System.out.println("k");
            } catch (IOException e) {
                Log.ERROR.log("IOException reading stream fully for thread: " + threadName, e);
            }
        }, threadName);
        thread.setDaemon(false);
        thread.start();
        return thread;
    }
}
