package com.nija123098.sithreon.util;

import com.nija123098.sithreon.Config;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Defines a simple logging system with five levels.
 * <p>
 * {@link Log#ERROR} will exit the application
 * but allow {@link Runtime} shutdown hooks to execute.
 */
public enum Log {
    /**
     * For fatal occurrences.
     */
    ERROR(31) {
        @Override
        public void log(String log) {
            super.log(log);
            writeError(log);
            System.exit(-1);
        }
    },
    /**
     * For exceptions which may cause problems.
     */
    WARN(33),
    /**
     * For general semi-important info.
     */
    INFO(34),
    /**
     * For info plausibly useful in debugging.
     */
    DEBUG(35),
    /**
     * For info likely not useful in debugging.
     */
    TRACE(37),;
    /**
     * The {@link DateFormat} for the log prefix.
     */
    private static final DateFormat FORMAT = new SimpleDateFormat("EE MM/dd/yy HH:mm:ss:SSS");
    /**
     * The {@link Path} to save the log file to.
     */
    private static final Path LOG_PATH = Paths.get("log", Long.toString(System.currentTimeMillis()) + ".log").toAbsolutePath();

    static {
        try {
            Files.createDirectories(LOG_PATH.getParent());
        } catch (IOException e) {
            Log.ERROR.log("Could not create log parent directory", e);
        }
    }

    /**
     * The log color value as an integer representation.
     */
    private int colorValue;

    Log(int colorValue) {
        this.colorValue = colorValue;
    }

    /**
     * Gets the prefix for information for the logger.
     *
     * @return the prefix for information for the logger.
     */
    private static String getPrefix(Log level) {
        return level.name() + (level.name().length() == 4 ? " " : "") + " " + FORMAT.format(new Date()) + " [" + Thread.currentThread().getName() + "] - ";
    }

    /**
     * Writes an {@link Log#ERROR} message to the development home directory.
     *
     * @param text the {@link Log#ERROR} message.
     */
    private static void writeError(String text) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, (getPrefix(ERROR) + text).replace("\r", "").split("\n"));
        try {
            Files.write(Paths.get("ERROR.txt"), list, StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.println("Could not write ERROR.txt");
            e.printStackTrace();
        }
    }

    public void log(String log) {
        if (Config.logLevel.ordinal() >= this.ordinal()) {
            log = "\u001B[" + this.colorValue + "m" + getPrefix(this) + log + "\u001B[0m";
            System.out.println(log);
            try {
                Files.write(LOG_PATH, Collections.singletonList(log), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Could not write to log file");
                e.printStackTrace();
                System.err.println("Could not log:\n" + log);
            }
        }
    }

    public void log(String log, Throwable throwable) {
        this.log(log + "\n" + ExceptionUtils.getStackTrace(throwable));
    }
}
