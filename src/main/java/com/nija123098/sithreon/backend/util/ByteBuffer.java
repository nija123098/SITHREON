package com.nija123098.sithreon.backend.util;

import java.util.ArrayList;

/**
 * A simple {@link ArrayList} implementation
 * with a few byte oriented helper methods.
 *
 * @author nija123098
 */
public class ByteBuffer extends ArrayList<Byte> {
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
     * Gets the bytes of the buffer instance in a range.
     *
     * @param remove if the gotten bytes should be
     *               removed from this buffer.
     * @param source the starting index to start getting bytes from.
     * @param length the number of bytes to get.
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
        this.ensureCapacity(this.size() + bytes.length);
        for (byte b : bytes) this.add(b);
    }

    public void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
    }
}
