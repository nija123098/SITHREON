package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.connection.ConnectionException;
import com.nija123098.sithreon.backend.util.throwable.connection.GeneralConnectionException;
import com.nija123098.sithreon.backend.util.throwable.connection.SpecificConnectionException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A general helper utility for connections.
 */
public class ConnectionUtil {

    /**
     * Checks if there is a host reachable by the given hostname.
     *
     * @param host the hostname.
     * @return if the host is reachable.
     */
    private static boolean checkConnection(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(null, 0, 500)) return true;
            return new Socket(address, 80).isBound();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the program has a connection to common servers.
     *
     * @return if the program has a connection to common servers.
     */
    public static boolean hasGeneralConnection() {
        return Stream.of(Config.standardAccessibleDomains.split(Pattern.quote(","))).map(String::trim).parallel().noneMatch(s -> {
            boolean b = !checkConnection(s);
            if (b) Log.WARN.log("Unable to connect to " + s);
            return b;
        });
    }

    /**
     * Throws an exception with the provided issue message, or a variation of it.
     * The exception type depends on if a general connection domains are reachable.
     *
     * @param issue the issue message of the exception.
     * @throws GeneralConnectionException  if a general connection domain can not be reached.
     * @throws SpecificConnectionException if all general connections are reachable.
     */
    public static void throwConnectionException(String issue) {
        throwConnectionException(issue, null);
    }

    /**
     * Throws an exception with the provided issue message, or a variation of it.
     * The exception type depends on if a general connection domains are reachable.
     *
     * @param issue the issue message of the exception.
     * @param cause the cause of the exception to throw.
     * @throws GeneralConnectionException  if a general connection domain can not be reached.
     * @throws SpecificConnectionException if all general connections are reachable.
     */
    public static void throwConnectionException(String issue, Exception cause) {
        if (hasGeneralConnection()) {
            if (cause == null) throw new SpecificConnectionException(issue);
            else throw new SpecificConnectionException(issue, cause);
        } else {
            if (cause == null) throw new GeneralConnectionException(issue);
            else throw new GeneralConnectionException(issue, cause);
        }
    }

    /**
     * Check if the page exists.
     *
     * @param page the page to check.
     * @return if the page exists.
     * @throws ConnectionException when no general connection exists.
     */
    public static boolean pageExists(String page) {
        try {
            boolean pageExists = ((HttpURLConnection) new URL(page).openConnection()).getResponseCode() == HttpURLConnection.HTTP_OK;
            if (!pageExists && !hasGeneralConnection())
                throw new GeneralConnectionException("Unable to connect generally");
            return pageExists;
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Exception checking if page exists: " + page, e);
            throw new NoReturnException();
        }
    }
}
