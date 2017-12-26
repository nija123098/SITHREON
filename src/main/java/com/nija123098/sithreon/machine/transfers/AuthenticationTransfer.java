package com.nija123098.sithreon.machine.transfers;

import com.nija123098.sithreon.Config;
import com.nija123098.sithreon.machine.Machine;
import com.nija123098.sithreon.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * The {@link Transfer} which responds for authentication.
 *
 * @author nija123098
 */
public class AuthenticationTransfer extends Transfer<Machine> {
    /**
     * The hashing algorithm represented as a {@link MessageDigest}.
     */
    private static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance(Config.hashingAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            Log.ERROR.log("Invalid security settings", e);
            throw new RuntimeException();// Won't occur
        }
    }

    /**
     * The hash of the time, key, and hash request.
     */
    private final byte[] hash;

    /**
     * Constructs an authentication transfer to attempt to validate the connected server.
     *
     * @param hashRequest the bytes requested to be part of the digestion.
     * @param time        the time quested to be part of the digestion.
     */
    AuthenticationTransfer(byte[] hashRequest, long time) {
        this.hash = this.getHash(hashRequest, time);
    }

    /**
     * Gets the bytes representing the input long.
     *
     * @param time the time to get the byte array representation for.
     * @return the byte array representation of the long.
     */
    private static byte[] getBytes(long time) {
        return ByteBuffer.allocate(Long.BYTES).putLong(0, time).array();
    }

    /**
     * Gets the hash of the bytes of the time, key, and hashRequest in that order.
     *
     * @param hashRequest the thing to hash with the time and key.
     * @return the hash of the time, key, and hash request.
     */
    private byte[] getHash(byte[] hashRequest, long time) {
        byte[] digestion = new byte[Config.authenticationKey.length() + Long.BYTES + hashRequest.length];
        System.arraycopy(getBytes(time), 0, digestion, 0, Long.BYTES);
        try {
            byte[] authenticationBytes = Config.authenticationKey.getBytes("UTF-8");
            System.arraycopy(authenticationBytes, 0, digestion, Long.BYTES, authenticationBytes.length);
            System.arraycopy(hashRequest, 0, digestion, authenticationBytes.length + Long.BYTES, hashRequest.length);
            return DIGEST.digest(digestion);// hashes the digestion
        } catch (UnsupportedEncodingException e) {
            Log.ERROR.log("UTF-8 is apparently no longer a thing", e);
            return null;// This won't occur
        }
    }

    /**
     * Checks if the hash contained in this instance is the same
     * as what should be generated with the correct key.
     *
     * @param hashRequest the bytes to hash with the time and key.
     * @param time        the time that should be hashed with the hash request and key.
     * @return if the hash is the expected hash.
     */
    public boolean isAuthentic(byte[] hashRequest, long time) {
        byte[] hash = this.getHash(hashRequest, time);
        return time > System.currentTimeMillis() - 15_000 && time <= System.currentTimeMillis() && Arrays.equals(hash, this.hash);
    }

    @Override
    public void act() {
    }
}
