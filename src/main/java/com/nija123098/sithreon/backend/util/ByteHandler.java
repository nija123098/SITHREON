package com.nija123098.sithreon.backend.util;

import java.util.ArrayList;

/**
 * A simple {@link ArrayList} implementation
 * with a few byte oriented helper methods.
 *
 * @author nija123098
 */
public class ByteHandler extends ArrayList<Byte> {

    /**
     * Initializes instance with no initial buffer.
     */
    public ByteHandler() {
    }

    /**
     * Initializes instance with the given bytes.
     *
     * @param bytes the bytes to add at initialization.
     */
    public ByteHandler(byte[] bytes) {
        this.add(bytes);
    }

    /**
     * Gets all bytes of the buffer.
     *
     * @return all bytes of the buffer.
     */
    public byte[] getBytes() {
        byte[] bytes = new byte[this.size()];
        for (int i = 0; i < this.size(); i++) bytes[i] = this.get(i);
        return bytes;
    }

    /**
     * Gets the bytes of the buffer instance in a range.
     *
     * @param remove if the bytes gotten should be
     *               removed from this buffer.
     * @param length the number of bytes to get.
     * @return the bytes gotten.
     */
    public byte[] getBytes(boolean remove, int length) {
        return this.getBytes(remove, 0, length);
    }

    /**
     * Gets the bytes of the buffer instance from source to the length number of bytes.
     * <p>
     * Note the length is effectively the end point inclusive,
     * but is only meant to be the length of bytes to read.
     *
     * @param remove if the gotten bytes should be
     *               removed from this buffer.
     * @param source the starting index to start getting bytes from.
     * @param length the number of bytes to get, not the end point.
     * @return the bytes gotten.
     */
    public byte[] getBytes(boolean remove, int source, int length) {
        if (this.size() < source + length)
            throw new ArrayIndexOutOfBoundsException("Insufficient bytes in list to put to array");
        byte[] ret = new byte[length];
        for (int i = source; i < source + length; i++) ret[i - source] = this.get(i);
        if (remove) this.removeRange(source, source + length);
        return ret;
    }

    /**
     * Adds all the bytes in the array in the order provided.
     *
     * @param bytes the bytes to add to the buffer.
     */
    public void add(byte[] bytes) {
        this.add(bytes, 0, bytes.length);
    }

    /**
     * Adds all the bytes in the array in the order provided.
     *
     * @param bytes  the bytes to add to the buffer.
     * @param length the length of bytes to add.
     */
    public void add(byte[] bytes, int length) {
        this.add(bytes, 0, length);
    }

    /**
     * Adds all the bytes in the array in the order provided.
     *
     * @param bytes the bytes to add to the buffer.
     * @param from  the location on the array to start.
     * @param to    the location on the array to finish.
     */
    public void add(byte[] bytes, int from, int to) {
        this.ensureCapacity(this.size() + to - from);
        for (int i = from; i < to; i++) this.add(bytes[i]);
    }

    public void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
    }
}
