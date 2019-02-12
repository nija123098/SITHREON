package com.nija123098.sithreon.backend.networking;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ObjectSerializationTest {

    private static <E> void test(Class<E> clazz, E e, byte[] expectedRep) {
        byte[] bytes = ObjectSerialization.serialize(clazz, e);
        assertArrayEquals(expectedRep, bytes);
        assertEquals(e, ObjectSerialization.deserialize(clazz, bytes));
    }

    @Test
    public void testLong() {// 2's complement
        test(Long.class, 0L, new byte[Long.BYTES]);
        test(Long.class, 10L, new byte[]{0, 0, 0, 0, 0, 0, 0, 10});
        test(Long.class, -13L, new byte[]{-1, -1, -1, -1, -1, -1, -1, -13});
        test(Long.class, Long.MAX_VALUE, new byte[]{127, -1, -1, -1, -1, -1, -1, -1});// signed long max
        test(Long.class, Long.MIN_VALUE, new byte[]{-128, 0, 0, 0, 0, 0, 0, 0});// signed long max
    }

    @Test
    public void testInt() {
        test(Integer.class, 0, new byte[Integer.BYTES]);
        test(Integer.class, 21, new byte[]{0, 0, 0, 21});
        test(Integer.class, -32, new byte[]{-1, -1, -1, -32});
        test(Integer.class, Integer.MAX_VALUE, new byte[]{127, -1, -1, -1});// signed long max
        test(Integer.class, Integer.MIN_VALUE, new byte[]{-128, 0, 0, 0});// signed long max
    }

    @Test
    public void testString() {
        test(String.class, "", new byte[0]);
        test(String.class, "1", new byte[]{'1'});
        test(String.class, "1234", new byte[]{'1', '2', '3', '4'});
    }

    @Test
    public void testBoolean() {
        test(Boolean.class, true, new byte[]{1});
        test(Boolean.class, false, new byte[]{0});
    }

    @Test
    public void testByteArray() {
        test(byte[].class, new byte[0], new byte[0]);
        test(byte[].class, new byte[]{1, 2, 3}, new byte[]{1, 2, 3});
    }
}
