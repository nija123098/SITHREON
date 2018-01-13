package com.nija123098.sithreon.backend.util;

/**
 * A utility that provides help when working with {@link String}s.
 */
public class StringHelper {
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
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length - 1; i++) builder.append(args[i]).append(splitter);
        return builder.append(args[args.length - 1]).toString();
    }
}
