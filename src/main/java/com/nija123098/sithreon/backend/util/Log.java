package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.Config;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

/**
 * Defines a simple logging system with five levels.
 * <p>
 * {@link Log#ERROR} will exit the application
 * but allow {@link Runtime} shutdown hooks to execute.
 *
 * @author nija123098
 */
public enum Log {
    /**
     * For fatal occurrences.
     * This will result in the exiting of the program.
     */
    ERROR(31) {
        @Override
        public String log(String log) {
            writeError(super.log(log));
            System.exit(-1);
            return null;// won't occur
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
    TRACE(36),
    ;

    /**
     * The {@link DateFormat} for the log prefix.
     */
    private static final DateFormat FORMAT = new SimpleDateFormat("EE MM/dd/yy HH:mm:ss.SSS");

    /**
     * The {@link Path} to save the log file to.
     */// TODO sort loading order to properly load log destination
    private static final Path LOG_PATH = Paths.get("log", new SimpleDateFormat("MM-dd-yy__HH-mm-ss_SSS").format(new Date()) + ".log");

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

    /**
     * Initializes a logger level with a console color.
     *
     * @param colorValue the console color code to use for the level.
     */
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
        try {
            Files.write(Paths.get("ERROR.log"), Collections.singletonList(text), StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.err.println("Could not write ERROR.log");
            e.printStackTrace();
        }
    }

    /**
     * Logs information to the appropriate appeasers.
     *
     * @param log the information to log.
     * @return the logger text.
     */
    public String log(String log) {
        if (Config.logLevel.ordinal() >= this.ordinal()) {
            log = "\u001B[" + this.colorValue + "m" + getPrefix(this) + log.trim() + "\u001B[0m";
            System.out.println(log);
            try {
                Files.write(LOG_PATH, Collections.singletonList(log), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Could not write to log file");
                e.printStackTrace();
                System.err.println("Could not log:\n" + log);
            }
            return log;
        }
        return null;
    }

    /**
     * Logs information to the appropriate appeasers.
     *
     * @param log       the information to log.
     * @param throwable the {@link Throwable} to log a stacktrace of.
     * @return the logger text.
     */
    public String log(String log, Throwable throwable) {
        return this.log(log + "\n" + ExceptionUtils.getStackTrace(throwable));
    }
}
