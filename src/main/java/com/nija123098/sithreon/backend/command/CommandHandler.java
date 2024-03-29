package com.nija123098.sithreon.backend.command;

import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringUtil;
import com.nija123098.sithreon.backend.util.ThreadMaker;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Handles {@link Command} initialization and command invocation.
 * <p>
 * It is initialized with arguments for it's first command.
 * If the first command fails an {@link Log#ERROR} will be logged.
 *
 * @author nija123098
 */
public class CommandHandler {
    /**
     * The command map for command invocation optimization.
     */
    static final CommandMap COMMAND_MAP = new CommandMap<>();

    /**
     * The scanner for command processing, which needs to be used in commands with extended input.
     */
    static final Scanner SCANNER = new Scanner(new BufferedInputStream(System.in));

    /**
     * Initializes the command handler.
     *
     * @param args the initial command to run.
     */
    public static void init(String[] args) {
        ScanResult results = new FastClasspathScanner("com.nija123098.sithreon.backend.command.commands").matchSubclassesOf(Command.class, cls -> {
        }).scan();// Get all command classes and register them
        try {
            results.classNamesToClassRefs(results.getNamesOfAllClasses()).stream().filter(aClass -> !Modifier.isAbstract(aClass.getModifiers())).forEach(clazz -> {
                try {
                    clazz.newInstance();
                } catch (InstantiationException e) {
                    Log.ERROR.log("Exception loading native command: " + clazz.getName(), e);
                } catch (IllegalAccessException e) {
                    Log.ERROR.log("Malformed native command: " + clazz.getName(), e);
                }
            });
        } catch (Exception e) {
            Log.ERROR.log("Exception loading native commands", e);
        }
        String joined = StringUtils.join(args, " ");// Join main lines
        try {
            COMMAND_MAP.invokeCommand(joined, true);
        } catch (Exception e) {
            Log.ERROR.log("Exception running startup command: " + joined, e);
        }
        // Start handler thread
        ThreadMaker.getThread(ThreadMaker.BACKEND, "Command Handler Reader", true, () -> {
            String arg = null;
            while (true) {
                try {
                    arg = SCANNER.nextLine().trim();
                    COMMAND_MAP.invokeCommand(arg, false);
                } catch (NoSuchElementException e) {
                    return;// Return if System.in is closed.
                } catch (Exception e) {
                    Log.WARN.log("Caught exception attempting to invoke command: " + arg, e);
                }
            }
        }).start();
    }

    /**
     * Invokes a command specified by the command name in it's arguments.
     *
     * @param args  the command name and it's arguments.
     * @param fatal if the command should log an {@link Log#ERROR} if it fails.
     */
    public static void invokeCommand(String args, boolean fatal) {
        COMMAND_MAP.invokeCommand(args, fatal);
    }

    /**
     * A simple nested map construct for the optimization of calling commands.
     *
     * @param <E> it's own type.
     */
    static class CommandMap<E> extends HashMap<String, CommandMap<E>> {
        private Command command;

        /**
         * Registers a command into the command map.
         *
         * @param command the command to register.
         * @param name    the name to register the command at.
         */
        void putCommand(Command command, String... name) {
            putCommand(command, 0, name);
        }

        /**
         * Registers a command into the command map.
         * <p>
         * This recursively registers the name through the command map hierarchy.
         *
         * @param command   the command to register.
         * @param nameIndex the index of the name to be currently registered.
         * @param name      the name to register the command at.
         */
        private void putCommand(Command command, int nameIndex, String... name) {
            if (name.length == nameIndex) {
                if (this.command != null) {
                    Log.WARN.log("Registering with name " + StringUtil.join(" ", name) + " over command " + this.command.getClass().getName() + " with " + command.getClass().getName());
                }// WARN level for loading custom commands
                this.command = command;
            } else {
                this.computeIfAbsent(name[nameIndex], (n) -> new CommandMap<>()).putCommand(command, ++nameIndex, name);
            }
        }

        /**
         * Invokes the command with only it's arguments.
         *
         * @param args  the arguments of the command to be changed into objects.
         * @param fatal if the command should log an {@link Log#ERROR} if it fails.
         */
        private void invokeCommand(String args, boolean fatal) {
            args = args.trim();
            if (args.isEmpty()) return;
            String[] split = StringUtil.removeRepeats(args, ' ').split(Pattern.quote(" "));
            Command command = null;
            CommandMap<E> map = this;
            int nameLength = 0;
            int namePosition = 0;
            for (; namePosition < split.length; namePosition++) {
                map = map.get(split[namePosition]);
                if (map == null) break;
                if (map.command != null) {
                    command = map.command;
                    nameLength = namePosition;
                }
            }
            if (command == null) Log.WARN.log("Unrecognized command: " + args);
            else {
                int cut = 0;
                for (int i = 0; i < nameLength + 1; i++) {
                    cut += split[i].length();
                    while (args.length() > cut + 1 && args.charAt(cut) == ' ') {
                        ++cut;
                    }
                }
                String arg = args.substring(cut);
                Log.TRACE.log("Invoking command: " + command.getClass().getName() + " " + arg);
                command.invoke(arg, fatal);
                Log.TRACE.log("Completed command invocation: " + command.getClass().getName() + " " + arg);
            }
        }
    }
}
