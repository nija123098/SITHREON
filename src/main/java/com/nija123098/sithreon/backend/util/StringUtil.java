package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonSecurityException;
import jdk.nashorn.api.scripting.URLReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

/**
 * A utility that provides help when working with {@link String}s.
 */
public class StringUtil {
    /**
     * Removes the repeated characters.
     *
     * @param s the {@link String} to remove extra characters from.
     * @param c the character to remove.
     * @return the cleaned string.
     */
    public static String removeRepeats(String s, char c) {
        StringBuilder builder = new StringBuilder();
        boolean keep = true;
        for (char ch : s.toCharArray()) {
            if (ch == c) {
                if (keep) {
                    builder.append(ch);
                    keep = false;
                }
            } else {
                builder.append(ch);
                keep = true;
            }
        }
        return builder.toString();
    }

    /**
     * Adds a string between provided strings.
     *
     * @param splitter the sting to insert between the provided strings.
     * @param args     the strings to add splitting between.
     * @return the provided strings with the splitter added between.
     */
    public static String join(String splitter, String... args) {
        StringBuilder builder = new StringBuilder(splitter.length() * 4 * args.length);
        for (int i = 0; i < args.length - 1; i++) builder.append(args[i]).append(splitter);
        return builder.append(args[args.length - 1]).toString();
    }

    /**
     * Reads the file contents at the given URL.
     *
     * @param url the URL to read.
     * @return the list of strings of the URL.
     */
    public static List<String> readRaw(String url) {
        try {
            BufferedReader reader = new BufferedReader(new URLReader(new URL(url)));
            String content;
            List<String> lines = new LinkedList<>();
            while ((content = reader.readLine()) != null) lines.add(content);
            return lines;
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to connect to url: " + url, e);
            throw new NoReturnException();
        }
    }

    /**
     * Returns a substring of the provided string, ending exclusively with the first instance of the end string.
     *
     * @param string the string to make a substring of.
     * @param end    the string to end at.
     * @return the substring of the first string ending exclusively with the second.
     */
    public static String endAt(String string, String end) {
        int index = string.indexOf(end);
        return index == -1 ? string : string.substring(0, index);
    }

    /**
     * Encodes to base 64 in MIME format with no new lines.
     *
     * @param bytes the bytes to encode
     * @return The MIME encoded bytes with no new lines.
     */
    public static String base64EncodeOneLine(byte... bytes) {
        return new String(Base64.getMimeEncoder(Integer.MAX_VALUE, new byte[0]).encode(bytes), StandardCharsets.UTF_8);
    }

    private static final MessageDigest MESSAGE_DIGEST;

    static {
        try {
            MESSAGE_DIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new SithreonSecurityException("No SHA-256 implementation found", e);
        }
    }

    /**
     * Hashes a string's content by SHA-256 and returns the bytes.
     *
     * @param input the string to hash.
     * @return the SHA-256 of the hash bytes.
     */
    public static byte[] getSha256Hash(String input) {
        return MESSAGE_DIGEST.digest(input.getBytes(StandardCharsets.UTF_8));
    }
}
