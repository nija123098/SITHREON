package com.nija123098.sithreon.backend;

import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
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
    public static final Path CONFIG_PATH = Paths.get("config.cfg");

    // AUTHENTICATION

    /**
     * The Object Identifier to represent {@link MachineAction} permissions.
     */
    public static String certificatePermissionOID;

    /**
     * Enables authentication using asymmetric cryptography and certificates.
     */
    public static Boolean authenticateMachines;

    /**
     * The private key for this {@link Machine}.
     */
    public static PrivateKey privateKey;

    /**
     * The serial number of the certificate representing this machine.
     */
    public static BigInteger selfCertificateSerial;

    /**
     * The key store location to use for storing SITHREON related certificates.
     * <p>
     * Do not combine with other key stores for different purposes.
     */
    public static File trustedCertificateStorage = new File("KeyStore.jks");

    /**
     * The password for the key store.
     */
    public static char[] keyStorePassword = new char[0];

    // NETWORKING

    /**
     * The address of the super server.
     */
    public static String superServerAddress;

    /**
     * The address of the game server.
     */
    public static String gameServerAddress;

    /**
     * The port to communicate externally on.
     */
    public static Integer externalPort;

    /**
     * The port to communicate internally on.
     */
    public static Integer internalPort;

    // MISCELLANEOUS

    /**
     * The unique id of the machine.
     */
    public static String machineId;

    /**
     * The priority this client has to serve a server.
     */
    public static Integer priority;

    /**
     * The number of milliseconds between database saves.
     */
    public static Integer databaseSaveDelay;

    /**
     * The comma separated list of domains or domains to check if dependent connections are up.
     */
    public static String standardAccessibleDomains;

    /**
     * If connection should use secure protocols.
     */
    public static Boolean useSecure;

    /**
     * The level to display logs at.
     */
    public static Log logLevel = Log.TRACE;

    /**
     * If the config should be deleted on startup.
     */
    private static Boolean removeConfig = false;

    /**
     * The function map for converting simple values to
     */
    private static final Map<Class<?>, Function<String, ?>> FUNCTION_MAP = new HashMap<>();

    /**
     * Converts a string to an enum instance given the {@link Class}.
     *
     * @param clazz the type of the enum.
     * @param s     the string representation.
     * @param <E>   the type of the enum.
     * @return the enum represented by the string.
     */
    private static <E extends Enum<E>> E getEnum(Class<?> clazz, String s) {
        return Enum.valueOf((Class<E>) clazz, s);
    }

    /**
     * Loads all the key value config to the fields in the provided object.
     *
     * @param object the object to load to.
     * @param lines  the lines of the file.
     */
    public static void setValues(Object object, List<String> lines) {
        if (object == null) throw new NullPointerException();
        Class<?> clazz = object instanceof Class<?> ? (Class<?>) object : object.getClass();
        lines.stream().map(s -> {
            int index = s.indexOf('#');
            return index == -1 ? s : s.substring(0, index);
        }).forEach(s -> {
            int index = s.indexOf('=');
            if (index == -1) return;
            String configName = s.substring(0, index).trim();
            try {
                Field field = clazz.getField(configName);
                Class<?> type = field.getType();
                try {
                    Object o;
                    String value = s.substring(index + 1).trim();
                    if (type.isEnum()) o = getEnum(type, value.toUpperCase().replace(" ", "_"));
                    else {
                        Function<String, ?> conversionFunction = FUNCTION_MAP.get(type);
                        if (conversionFunction == null)
                            throw new SithreonException("Unable to convert strings to " + type.getName());
                        o = conversionFunction.apply(value);
                    }
                    field.set(object, o);
                } catch (IllegalAccessException e) {
                    Log.WARN.log("Unable to assign value to " + field.getName(), e);
                } catch (Exception e) {
                    throw new SithreonException("Unable to load config for " + object + " due to line " + s, e);
                }
            } catch (NoSuchFieldException e) {
                Log.DEBUG.log("Config in " + object + " unused: " + configName);
            }
        });
    }

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

    public static <E> Function<String, E> getFunction(Class<E> type) {
        return (Function<String, E>) FUNCTION_MAP.get(type);
    }

    static {
        FUNCTION_MAP.put(String.class, (s) -> s);
        FUNCTION_MAP.put(Integer.class, Integer::parseInt);
        FUNCTION_MAP.put(Long.class, Long::valueOf);
        FUNCTION_MAP.put(Log.class, Log::valueOf);
        FUNCTION_MAP.put(Boolean.class, Boolean::valueOf);
        FUNCTION_MAP.put(byte[].class, s -> Base64.getMimeDecoder().decode(s));
        FUNCTION_MAP.put(char[].class, String::toCharArray);
        FUNCTION_MAP.put(File.class, File::new);
        FUNCTION_MAP.put(Path.class, Paths::get);
        FUNCTION_MAP.put(BigInteger.class, s -> new BigInteger(Base64.getMimeDecoder().decode(s)));
        FUNCTION_MAP.put(PrivateKey.class, s -> {
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(s)));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                Log.ERROR.log("Invalid security settings");
                throw new NoReturnException();
            }
        });
        FUNCTION_MAP.put(PublicKey.class, s -> {
            try {
                return KeyFactory.getInstance("RSA").generatePublic(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(s)));
            } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                Log.ERROR.log("Invalid security settings");
                throw new NoReturnException();
            }
        });
        FUNCTION_MAP.put(MachineAction[].class, s -> Stream.of(s.split(Pattern.quote(","))).map(MachineAction::valueOf).toArray(MachineAction[]::new));
        try {
            setValues(Config.class, Files.readAllLines(CONFIG_PATH));
        } catch (Exception e) {
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
