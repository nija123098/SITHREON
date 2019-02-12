package com.nija123098.sithreon.backend.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class ByteHandlerTest {

    @Test
    public void getBytes() {
        byte[] bytes = new byte[]{10, 9, 8, 7, -6, -5, 4};
        assertArrayEquals(bytes, new ByteHandler(bytes).getBytes());
    }

    @Test
    public void getBytesLength() {
        byte[] bytes = new byte[]{10, 9, 8, 7, -6, -5, 4};
        ByteHandler handler = new ByteHandler(bytes);
        assertArrayEquals(bytes, handler.getBytes(false, bytes.length));
        assertArrayEquals(new byte[]{10, 9, 8}, handler.getBytes(true, 3));
        assertArrayEquals(new byte[]{7, -6, -5, 4}, handler.getBytes(true, 4));
        assertTrue(handler.isEmpty());
    }

    @Test
    public void getBytesRange() {
        byte[] bytes = new byte[]{10, 9, 8, 7, -6, -5, 4};
        ByteHandler handler = new ByteHandler(bytes);
        assertArrayEquals(new byte[]{9, 8, 7}, handler.getBytes(true, 1, 3));
        assertArrayEquals(new byte[]{-6}, handler.getBytes(false, 1, 1));
        assertArrayEquals(new byte[]{-6, -5}, handler.getBytes(false, 1, 2));
    }

    @Test
    public void add() {
        ByteHandler handler = new ByteHandler(new byte[]{12, 11});
        handler.add(new byte[]{10, 9, 8});
        assertArrayEquals(new byte[]{12, 11, 10, 9, 8}, handler.getBytes());
    }

    @Test
    public void addLength() {
        ByteHandler handler = new ByteHandler(new byte[]{12, 11});
        handler.add(new byte[]{10, 9, 8, 7}, 2);
        assertArrayEquals(new byte[]{12, 11, 10, 9}, handler.getBytes());
    }

    @Test
    public void addBounds() {
        ByteHandler handler = new ByteHandler(new byte[]{12, 11});
        handler.add(new byte[]{10, 9, 8, 7}, 1, 3);
        assertArrayEquals(new byte[]{12, 11, 9, 8}, handler.getBytes());
    }
}
