package com.nija123098.sithreon.backend.command;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.FunctionPair;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.StringHelper;
import javafx.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An abstract class representing a command which can be invoked
 * by typing a name or alias in the command prompt.
 * <p>
 * The method to be invoked for the command invocation
 * must be annotated with {@link CommandMethod}.
 *
 * @author nija123098
 */
public abstract class Command {
    /**
     * Maps a {@link Class} to a {@link Conversion} object.
     */
    private static final Map<Class<?>, Conversion<Object>> CONVERSION_MAP = new HashMap<>();

    /**
     * Maps a {@link Class} to a {@link Supplier} object.
     */
    private static final Map<Class<?>, Object> DEFAULT_MAP = new HashMap<>();

    static {
        registerConversion(String.class, in -> new Pair<>(in, in.length()));
        registerConversion(Integer.class, in -> {
            in = StringHelper.endAt(in, " ");
            try {
                return new Pair<>(Integer.valueOf(in.replace("_", "").replace(",", "")), in.length());
            } catch (NumberFormatException e) {
                return null;
            }
        });
        registerConversion(Repository.class, in -> {
            in = StringHelper.endAt(in, " ");
            return new Pair<>(Repository.getRepo(in.endsWith("/") ? in.substring(0, in.length() - 1) : in), in.length());
        });
        Set<String> trueReps = new HashSet<>(), falseReps = new HashSet<>();
        Collections.addAll(trueReps, "true", "t", "y", "yes", "1");
        Collections.addAll(falseReps, "false", "f", "n", "no", "0");
        registerConversion(Boolean.class, in -> {
            in = StringHelper.endAt(in, " ");
            if (trueReps.contains(in)) return new Pair<>(true, in.length());
            if (falseReps.contains(in)) return new Pair<>(false, in.length());
            return null;
        });
    }

    /**
     * Registers a conversion from a {@link String} to an instance of the given class type.
     *
     * @param clazz      the type to register conversions for.
     * @param conversion the conversion to register.
     * @param <E>        the type to convert a {@link String} to.
     */
    private static <E> void registerConversion(Class<E> clazz, Conversion<E> conversion) {
        CONVERSION_MAP.put(clazz, (Conversion<Object>) conversion);
    }

    static {
        registerDefault(Machine.class, Machine.MACHINE::get);
        registerDefault(Boolean.class, () -> false);
    }

    /**
     * Registers a default value for a class type.
     *
     * @param clazz           the type to register conversions for.
     * @param defaultSupplier the supplier to get defaults with.
     * @param <E>             the type to convert a {@link String} to.
     */
    private static <E> void registerDefault(Class<E> clazz, Supplier<E> defaultSupplier) {
        DEFAULT_MAP.put(clazz, defaultSupplier);
    }

    /**
     * The method which's invocation is the invocation of the command.
     */
    private final Method method;

    /**
     * The cached argument types for the command's method.
     */
    private final Class<?>[] argTypes;

    /**
     * Constructs and does checks for a command.
     *
     * @param name the name to register the command for.
     */
    public Command(String... name) {
        Class<? extends Command> clazz = this.getClass();
        Method[] methods = clazz.getMethods();
        List<Method> commandMethods = Stream.of(methods).filter(m -> m.getAnnotation(CommandMethod.class) != null).collect(Collectors.toList());
        if (commandMethods.isEmpty()) Log.ERROR.log("No method named \"command\" in " + clazz.getName());
        if (commandMethods.size() > 1) Log.ERROR.log("Too many methods named \"command\" in " + clazz.getName());
        this.method = commandMethods.get(0);
        this.argTypes = this.method.getParameterTypes();
        for (Class<?> c : this.argTypes) {
            if (!CONVERSION_MAP.containsKey(c) && !DEFAULT_MAP.containsKey(c)) {
                Log.ERROR.log("No registered conversion for String to " + c.getSimpleName() + " in " + this.getClass().getName());
            }
        }
        CommandHandler.COMMAND_MAP.putCommand(this, name);
    }

    /**
     * Registers an additional name to invoke the command by.
     *
     * @param alias the name to register.
     */
    protected void registerAlias(String... alias) {
        CommandHandler.COMMAND_MAP.putCommand(this, alias);
    }

    /**
     * Invokes the command and converts objects to the relative arguments.
     *
     * @param args the arguments, before conversion to objects, to invoke commands by.
     */
    void invoke(String args, boolean initial) {
        Object[] objectArguments = new Object[this.argTypes.length];
        Pair<Object, Integer> pair;
        String rollingArgs = args;
        for (int i = 0; i < this.argTypes.length; i++) {
            if (this.argTypes[i].isEnum()) {
                if (rollingArgs.contains(" ")) pair = null;
                else {
                    String arg = rollingArgs.substring(0, rollingArgs.indexOf(' '));
                    pair = new FunctionPair<>(Stream.of(this.argTypes[i].getEnumConstants()).filter(o -> ((Enum<?>) o).name().equalsIgnoreCase(arg)).findFirst().orElse(null), (e) -> e == null ? 0 : ((Enum<?>) e).name().length());
                }
            } else {
                try {
                    Conversion<Object> conversion = CONVERSION_MAP.get(this.argTypes[i]);
                    if (conversion == null) pair = null;
                    else pair = conversion.apply(rollingArgs);
                } catch (Exception e) {
                    Log.WARN.log("Unable to invoke command due to exception converting arg " + i + " to an object", e);
                    return;
                }
            }
            if (pair == null) objectArguments[i] = ((Supplier<?>) DEFAULT_MAP.get(this.argTypes[i])).get();
            else {
                objectArguments[i] = pair.getKey();
                if (!rollingArgs.isEmpty() && rollingArgs.charAt(0) == ' ') {
                    for (int j = pair.getValue(); j < rollingArgs.length(); j++) {
                        if (rollingArgs.charAt(j) != ' ') rollingArgs = rollingArgs.substring(j - 1);
                    }
                }
            }
        }
        try {
            this.method.invoke(this, objectArguments);
        } catch (IllegalAccessException e) {
            Log.WARN.log("Malformed command method for " + this.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            (initial ? Log.ERROR : Log.WARN).log("Exception occurred during command invocation: " + this.getClass().getName() + " " + args, e);
        }
    }

    /**
     * Gets a {@link String} of helpful information about the command.
     *
     * @return a {@link String} of helpful information about the command.
     */
    protected String getHelp() {
        return "No help provided";
    }

    /**
     * A {@link Function} for converting a string to pair containing
     * the equivalent object and the number of characters taken to convert.
     *
     * @param <E> the type to convert a {@link String} to.
     */
    private interface Conversion<E> extends Function<String, Pair<E, Integer>> {
    }
}
