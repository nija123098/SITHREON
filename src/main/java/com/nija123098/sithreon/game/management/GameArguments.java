package com.nija123098.sithreon.game.management;

import com.nija123098.sithreon.backend.networking.ObjectSerialization;
import com.nija123098.sithreon.backend.util.ByteHandler;
import com.nija123098.sithreon.backend.util.Log;

/**
 * Represents the arguments for a {@link GameAction} which must be wrapped in this.
 */
public class GameArguments {// todo make normal names and args, do not transfer by Java class name or do in a better way
    /**
     * The wrapped arguments.
     */
    private Object[] objects;
    public GameArguments(byte[] bytes) {
        ByteHandler byteHandler = new ByteHandler(bytes);
        Integer argLength = ObjectSerialization.deserialize(Integer.class, byteHandler.getBytes(true, Integer.BYTES));
        this.objects = new Object[argLength];
        Integer classLength, objectLength;
        for (int i = 0; i < argLength; i++) {
            classLength = ObjectSerialization.deserialize(Integer.class, byteHandler.getBytes(true, Integer.BYTES));
            String className = ObjectSerialization.deserialize(String.class, byteHandler.getBytes(true, classLength));
            objectLength = ObjectSerialization.deserialize(Integer.class, byteHandler.getBytes(true, Integer.BYTES));
            try {
                this.objects[i] = ObjectSerialization.deserialize(ClassLoader.getSystemClassLoader().loadClass(className), byteHandler.getBytes(true, objectLength));
            } catch (ClassNotFoundException e) {
                Log.WARN.log("Unrecognized class while loading arguments: " + className, e);
            }
        }
    }

    public GameArguments(Object... objects) {
        this.objects = objects;
    }

    /**
     * Gets the bytes representing this instance.
     *
     * @return the bytes representing this instance.
     */
    public byte[] getBytes() {
        ByteHandler bytes = new ByteHandler();
        bytes.add(ObjectSerialization.serialize(Integer.class, this.objects.length));
        for (Object object : this.objects) {
            Class<?> clazz = object.getClass();
            bytes.add(ObjectSerialization.serialize(Integer.class, clazz.getName().length()));
            bytes.add(ObjectSerialization.serialize(String.class, clazz.getName()));// Should probably add these methods to the ByteHandler
            byte[] objectBytes = getBytes(object);
            bytes.add(ObjectSerialization.serialize(Integer.class, objectBytes.length));
            bytes.add(objectBytes);
        }
        return bytes.getBytes();
    }
    private static <E> byte[] getBytes(E e) {
        return ObjectSerialization.serialize((Class<? super E>) e.getClass(), e);
    }

    /**
     * Gets the wrapped object at the specified index.
     *
     * @param index the index of the object.
     * @return the wrapped object at the index.
     */
    public Object getObject(int index) {
        return this.objects[index];
    }

    /**
     * Gets the wrapped objects.
     *
     * @return an array of the wrapped objects.
     */
    public Object[] getObjects() {
        return this.objects;
    }

}
