package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.MatchUp;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.ByteHandler;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

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
        registerSerialization(Long.class, (aLong -> ByteBuffer.allocate(Long.BYTES).putLong(aLong).array()), (bytes -> ((ByteBuffer) ByteBuffer.allocate(Long.BYTES).put(bytes).flip()).asLongBuffer().get()));
        registerSerialization(Integer.class, (aLong -> ByteBuffer.allocate(Integer.BYTES).putInt(aLong).array()), (bytes -> ((ByteBuffer) ByteBuffer.allocate(Integer.BYTES).put(bytes).flip()).asIntBuffer().get()));
        registerSerialization(String.class, s -> s.getBytes(StandardCharsets.UTF_8), bytes -> new String(bytes, Charset.forName("UTF-8")));
        registerSerialization(Repository.class, repository -> serialize(String.class, repository.toString()), bytes -> Repository.getRepo(deserialize(String.class, bytes)));
        registerSerialization(Boolean.class, bool -> new byte[]{bool ? ((byte) 1) : 0}, bytes -> bytes[0] == 1);
        registerSerialization(byte[].class, Function.identity(), Function.identity());
        registerSerialization(MatchUp.class, matchUp -> serialize(String.class, matchUp.toString()), bytes -> {
            String[] split = deserialize(String.class, bytes).split(Pattern.quote("+"));
            return new MatchUp(Repository.getRepo(split[0]), Repository.getRepo(split[1]));
        });
        registerSerialization(Match.class, match -> serialize(String.class, match.toString()), bytes -> {
            String[] split = deserialize(String.class, bytes).split(Pattern.quote("+"));
            return new Match(Repository.getRepo(split[0]), Repository.getRepo(split[1]), split[2], split[3], Long.parseLong(split[4]));
        });
        registerSerialization(Certificate.class, Certificate::getBytes, Certificate::getCertificate);
        registerSerialization(BigInteger.class, BigInteger::toByteArray, BigInteger::new);
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
        } else return ((Function<byte[], E>) TO_OBJECT.get(type)).apply(bytes);
    }
}
