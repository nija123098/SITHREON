package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.objects.*;
import com.nija123098.sithreon.backend.util.ByteHandler;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.throwable.SithreonException;
import com.nija123098.sithreon.game.management.GameArguments;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles the serialization of objects
 * for transfer between {@link Machine}s.
 *
 * @author nija123098
 */
public class ObjectSerialization {
    /**
     * Function for turning an enum to bytes, since in line ths doesn't seem to work.
     */
    private static final Function<Object, byte[]> ENUM_TO_BYTE = o -> new byte[]{(byte) ((Enum) o).ordinal()};

    /**
     * Map of deserialization functions.
     */
    private static final Map<Class<?>, Function<byte[], ?>> TO_OBJECT = new HashMap<>();

    /**
     * Map of serialization functions.
     */
    private static final Map<Class<?>, Function<?, byte[]>> TO_BYTES = new HashMap<>();

    static {
        registerSerialization(Long.class, (aLong -> ByteBuffer.allocate(Long.BYTES).putLong(aLong).array()), (bytes -> ((ByteBuffer) ByteBuffer.allocate(Long.BYTES).put(bytes).flip()).asLongBuffer().get()));// 2's complement
        registerSerialization(Integer.class, (aLong -> ByteBuffer.allocate(Integer.BYTES).putInt(aLong).array()), (bytes -> ((ByteBuffer) ByteBuffer.allocate(Integer.BYTES).put(bytes).flip()).asIntBuffer().get()));
        registerSerialization(String.class, s -> s.getBytes(StandardCharsets.UTF_8), bytes -> new String(bytes, Charset.forName("UTF-8")));
        registerStringSerialization(Repository.class, Repository::toString, Repository::getRepo);
        registerSerialization(Boolean.class, bool -> new byte[]{bool ? ((byte) 1) : 0}, bytes -> bytes[0] == 1);
        registerSerialization(byte[].class, Function.identity(), Function.identity());
        registerStringSerialization(MatchUp.class, MatchUp::toString, MatchUp::new);
        registerStringSerialization(Match.class, Match::toString, Match::new);
        registerStringSerialization(Lineup.class, Lineup::toString, Lineup::new);
        registerStringSerialization(Team.class, Team::toString, Team::new);
        registerStringSerialization(TeamMember.class, TeamMember::toString, TeamMember::new);
        registerStringSerialization(Competitor.class, Competitor::toString, Competitor::new);
        registerSerialization(Certificate.class, Certificate::getBytes, Certificate::getCertificate);
        registerSerialization(BigInteger.class, BigInteger::toByteArray, BigInteger::new);
        registerSerialization(GameArguments.class, GameArguments::getBytes, GameArguments::new);
    }

    /**
     * Registers methods to use for serialization and deserialization based on the type of an object.
     *
     * @param type     the type of the objects to serialize and deserialize.
     * @param toBytes  the function to for serialization.
     * @param toObject the function for deserialization.
     * @param <E>      the type being serialized and deserialized.
     */
    private static <E> void registerSerialization(Class<E> type, Function<E, byte[]> toBytes, Function<byte[], E> toObject) {
        TO_OBJECT.put(type, toObject);
        TO_BYTES.put(type, toBytes);
    }

    /**
     * Registers methods to use for serialization and deserialization based on the type of an object with a string intermediate step.
     *
     * @param type     the type of the objects to serialize and deserialize.
     * @param toBytes  the function to for serialization though strings.
     * @param toObject the function for deserialization through strings.
     * @param <E>      the type being serialized and deserialized.
     */
    private static <E> void registerStringSerialization(Class<E> type, Function<E, String> toBytes, Function<String, E> toObject) {
        TO_OBJECT.put(type, bytes -> toObject.apply(deserialize(String.class, bytes)));
        TO_BYTES.put(type, o -> serialize(String.class, toBytes.apply((E) o)));
    }

    /**
     * Serializes the given object.
     *
     * @param type   the type of the object being serialized.
     * @param object the object to serialize.
     * @param <E>    the type of the object being serialized.
     * @return the bytes representing the object.
     */
    public static <E> byte[] serialize(Class<E> type, E object) {
        if (type.isEnum()) return new byte[]{(byte) ((Enum) object).ordinal()};
        Function<E, byte[]> function = (Function<E, byte[]>) TO_BYTES.get(type);
        if (type.isArray() && function == null) {
            ByteHandler byteHandler = new ByteHandler();
            Function<Integer, byte[]> integerFunction = (Function<Integer, byte[]>) TO_BYTES.get(Integer.class);
            int length = Array.getLength(object);
            byteHandler.add(integerFunction.apply(length));
            function = (Function<E, byte[]>) (type.getComponentType().isEnum() ? ENUM_TO_BYTE : TO_BYTES.get(type.getComponentType()));
            byte[] serialized;
            for (int i = 0; i < length; i++) {
                serialized = function.apply((E) Array.get(object, i));
                byteHandler.add(integerFunction.apply(serialized.length));
                byteHandler.add(serialized);
            }
            return byteHandler.getBytes();
        } else return ((Function<E, byte[]>) TO_BYTES.get(type)).apply(object);
    }

    /**
     * Deserializes the given bytes to the represented object.
     *
     * @param type  the type of the object being deserialized.
     * @param bytes the bytes to deserialize.
     * @param <E>   the type of the object being deserialized.
     * @return the object represented by the bytes.
     */
    public static <E> E deserialize(Class<E> type, byte[] bytes) {
        if (type.isEnum()) return type.getEnumConstants()[bytes[0]];
        Function<byte[], E> function = (Function<byte[], E>) TO_OBJECT.get(type);
        if (type.isArray() && function == null) {
            ByteHandler byteHandler = new ByteHandler(bytes);
            Function<byte[], Integer> integerFunction = (Function<byte[], Integer>) TO_OBJECT.get(Integer.class);
            int length = integerFunction.apply(byteHandler.getBytes(true, Integer.BYTES));
            Object array = Array.newInstance(type.getComponentType(), length);
            function = type.getComponentType().isEnum() ? b -> (E) type.getComponentType().getEnumConstants()[b[0]] : (Function<byte[], E>) TO_OBJECT.get(type.getComponentType());
            for (int i = 0; i < length; i++) {
                Array.set(array, i, function.apply(byteHandler.getBytes(true, integerFunction.apply(byteHandler.getBytes(true, Integer.BYTES)))));
            }
            return (E) array;
        } else {
            function = (Function<byte[], E>) TO_OBJECT.get(type);
            if (function == null)
                Log.ERROR.log("ObjectSerialization does not support type: " + type, new SithreonException("No serialization support for " + type));
            return function.apply(bytes);
        }
    }
}
