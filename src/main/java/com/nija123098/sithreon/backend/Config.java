package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A simple static config for referencing constants.
 *
 * @author nija123098
 */
public class Config {
    /**
     * The {@link Path} where the config exists.
     */
    private static final Path CONFIG_PATH = Paths.get("config.cfg");

    /**
     * The algorithm for end to end encryption.
     */
    public static String encryptionAlgorithm;

    /**
     * The key for end to end encryption.
     */
    public static String encryptionKey;

    /**
     * The key for verification of a client.
     */
    public static String authenticationKey;

    /**
     * The hashing algorithm to use.
     */
    public static String hashingAlgorithm;

    /**
     * The address of the super server.
     */
    public static String superServerAddress;

    /**
     * The address of the game server.
     */
    public static String gameServerAddress;

    /**
     * The unique id of the machine.
     */
    public static String machineId;

    /**
     * The port to communicate on.
     */
    public static Integer port;

    /**
     * The level to display logs at.
     */
    public static Log logLevel = Log.TRACE;

    /**
     * If the config should be deleted on startup.
     */
    private static Boolean removeConfig = false;

    /**
     * Generates a config with the default values.
     */
    public static void generate() {
        if (Files.exists(CONFIG_PATH)) {
            Log.INFO.log("A config file already exists");
            return;
        }
        try {
            Files.write(CONFIG_PATH, Stream.of(Config.class.getDeclaredFields()).filter(field -> !Modifier.isFinal(field.getModifiers())).map(field -> {
                Object val = null;
                try {
                    val = field.get(Config.class);
                } catch (IllegalAccessException e) {
                    Log.ERROR.log("Malformed config class", e);
                }
                return field.getName() + "=" + (val == null ? "" : val);
            }).collect(Collectors.toList()));
        } catch (IOException e) {
            Log.ERROR.log("IOException generating config", e);
        }
    }

    /**
     * Sets the config field values to the values represented in the config file.
     */
    public static void init() {
        Map<Class<?>, Function<String, ?>> map = new HashMap<>();
        map.put(String.class, (s) -> s);
        map.put(Integer.class, Integer::parseInt);
        map.put(Log.class, Log::valueOf);
        map.put(Boolean.class, Boolean::valueOf);
        try {
            Files.readAllLines(CONFIG_PATH).forEach(s -> {
                int index = s.indexOf('=');
                if (index == -1) Log.ERROR.log("Malformed config file does not include =");
                String configName = s.substring(0, index);
                try {
                    Field field = Config.class.getDeclaredField(configName);
                    try {
                        field.set(Config.class, map.get(field.getType()).apply(s.substring(index + 1, s.length())));
                    } catch (IllegalAccessException e) {
                        Log.ERROR.log("Malformed config class", e);
                    } catch (Exception e) {
                        Log.ERROR.log("Unable to load config likely due to being unable to convert string to " + field.getType().getSimpleName(), e);
                    }
                } catch (NoSuchFieldException e) {
                    Log.DEBUG.log("Config contained unused config: " + configName);
                }
            });
        } catch (IOException e) {
            Log.ERROR.log("Unable to read config file", e);
        }
        if (Config.removeConfig) try {
            long size = Files.size(CONFIG_PATH);
            if (size > Integer.MAX_VALUE)
                Log.ERROR.log("Config file too large to write zeros too in the current version");
            Files.write(CONFIG_PATH, new byte[(int) size]);// write 0s
            Files.delete(CONFIG_PATH);
        } catch (IOException e) {
            Log.ERROR.log("IOException removing config file", e);
        }
    }
}
