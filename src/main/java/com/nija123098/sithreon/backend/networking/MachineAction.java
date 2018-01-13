package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.util.ByteBuffer;
import com.nija123098.sithreon.backend.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * An {@link Enum} representing the method for
 * invocation for transmitting {@link Command}s
 * other actions between machines between machines.
 *
 * Machine actions are compeleted by invoking a {@link Method}.
 *
 * @author nija123098
 */
public enum MachineAction {
    /** The action for initiating authentication. */
    REQUEST_AUTHENTICATION(TransferSocket.class),
    /** The action for authentication, the response to {@link MachineAction#REQUEST_AUTHENTICATION} */
    AUTHENTICATE(TransferSocket.class),
    /** The action to close all connected {@link Machine}s to close the entire network */
    CLOSE_ALL(Machine.class),;

    /**
     * The location to find the action's method.
     */
    private final Class<?> classLocation;

    /**
     * The method to be invoked to complete the action.
     */
    private final AtomicReference<Method> method = new AtomicReference<>();

    /**
     * The cached argument method argument types.
     */
    private final AtomicReference<Class<Object>[]> argumentTypes = new AtomicReference<>();

    /**
     * The cached value if this action is to be invoked on {@link Machine}s.
     */
    private final boolean machineAction;

    /**
     * Constructs an instance using a {@link Class} instance
     * as an indication to where the method is located.
     *
     * @param classLocation the {@link Class} type which the method
     *                      for this action is contained in.
     */
    MachineAction(Class<?> classLocation) {
        this.classLocation = classLocation;
        this.machineAction = Machine.class.isAssignableFrom(classLocation);
    }

    /**
     * Serializes the instance's ordinal and arguments
     * which the receiving machine will deserialize
     * to invoke on the appropriate method.
     *
     * @param args the object arguments for the connected machine
     *             to use as arguments for method invocation.
     * @return the bytes to transfer indicating a that the receiving machine
     * should invoke the indicated method with the specified arguments.
     */
    public byte[] write(Object... args) {
        this.ensureMethodLoad();
        if (this.argumentTypes.get().length != args.length)
            Log.ERROR.log("Invalid argument length for MachineAction " + this);
        for (int i = 0; i < this.argumentTypes.get().length; i++) {
            if (!this.argumentTypes.get()[i].isInstance(args[i]))
                Log.ERROR.log("Invalid argument given for MachineAction " + this + " at " + i + " expected " + this.argumentTypes.get()[i].getSimpleName() + " got " + args[i].getClass().getSimpleName());
        }
        if (this.argumentTypes.get().length == 0) return new byte[]{(byte) this.ordinal()};
        ByteBuffer bytes = new ByteBuffer();
        bytes.add((byte) this.ordinal());
        byte[] serialized;
        for (int i = 0; i < this.argumentTypes.get().length; i++) {
            serialized = ObjectSerialization.serialize(this.argumentTypes.get()[i], args[i]);
            bytes.add(ObjectSerialization.serialize(Integer.class, serialized.length));
            bytes.add(serialized);
        }
        return bytes.getBytes(false, bytes.size());
    }

    /**
     * Deserializes the bytes sent to this {@link Machine}
     * for invocation of the appropriate method.
     * <p>
     * The {@link ByteBuffer} will have the appropriate number of bytes
     * removed from it in the case that objects are found for deserialization.
     *
     * @param bytes the byte buffer to draw bytes from.
     * @return the objects to invoke on the appropriate method or null.
     * if the objects that should be specified are not completely within the buffer.
     */
    public Object[] read(ByteBuffer bytes) {
        this.ensureMethodLoad();
        if (this.argumentTypes.get().length == 0) return new Object[0];
        Object[] objects = new Object[this.argumentTypes.get().length];
        byte argument = 0;
        int serializeLength, objectIndex = 0;
        for (int i = 1; i < bytes.size(); i++) {// the first byte has already been used to determine that this
            if (bytes.size() < i + Integer.BYTES) return null;// instance should be used for handling the network input
            serializeLength = ObjectSerialization.deserialize(Integer.class, bytes.getBytes(false, i, Integer.BYTES));
            i += Integer.BYTES;
            if (bytes.size() < i + serializeLength) return null;
            objects[objectIndex++] = ObjectSerialization.deserialize(this.argumentTypes.get()[argument++], bytes.getBytes(false, i, serializeLength));
            i += serializeLength - 1;
            if (objectIndex >= this.argumentTypes.get().length) {
                bytes.removeRange(0, i + 1);
                return objects;
            }
        }
        return null;
    }

    /**
     * Invokes the command specified by this instance
     * with the deserialized objects as arguments.
     *
     * @param socket the socket to act on.
     * @param args   the object arguments to invoke th specified method.
     */
    public void act(TransferSocket socket, Object... args) {
        this.ensureMethodLoad();
        try {
            this.method.get().invoke(this.machineAction ? socket.getLocalMachine() : socket, args);
        } catch (IllegalAccessException e) {
            Log.ERROR.log("Malformed MachineAction reference method " + this.classLocation.getSimpleName() + "." + this.method.get().getName(), e);
        } catch (InvocationTargetException e) {
            Log.WARN.log("InvocationTargetException invoking " + this.classLocation.getSimpleName() + "." + this.method.get().getName(), e);
        }
    }

    /**
     * Ensures the initialization due to requiring the
     * instance to be initialized for this function.
     */
    private void ensureMethodLoad(){
        if (this.argumentTypes.get() != null) return;
        this.method.set(Stream.of(classLocation.getMethods()).filter(m -> {
            Action a = m.getAnnotation(Action.class);
            return a != null && a.value() == this;
        }).findFirst().orElse(null));
        if (this.method.get() == null) Log.ERROR.log("No method found for MachineAction " + this);
        this.argumentTypes.set((Class<Object>[]) this.method.get().getParameterTypes());
    }
}