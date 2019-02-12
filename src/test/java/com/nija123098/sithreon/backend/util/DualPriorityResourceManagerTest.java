package com.nija123098.sithreon.backend.util;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DualPriorityResourceManagerTest {
    private static final Integer A = 0, B = 1, C = 2, D = 3, E = 4;
    private static final Double V = 0.5, W = 1.5, X = 2.5, Y = 3.5, Z = 4.5;

    private final AtomicReference<Integer> intConsumed = new AtomicReference<>();
    private final AtomicReference<Double> doubleConsumed = new AtomicReference<>();
    private final DualPriorityResourceManager<Integer, Double> manager = new DualPriorityResourceManager<>((in, dou) -> {
        this.intConsumed.set(in);
        this.doubleConsumed.set(dou);
    });

    @Before
    public void setup() {
        this.manager.clear();
    }

    @Test
    public void giveFirst() {
        this.manager.giveFirst(A);
        assertNull(this.intConsumed.get());
        assertNull(this.doubleConsumed.get());
    }

    @Test
    public void giveSecond() {
        this.manager.giveSecond(V);
        assertNull(this.intConsumed.get());
        assertNull(this.doubleConsumed.get());
    }

    @Test
    public void giveBoth() {
        this.manager.giveFirst(A);
        this.manager.giveSecond(V);
        assertEquals(A, this.intConsumed.get());
        assertEquals(V, this.doubleConsumed.get());
        assertTrue(this.manager.isFirstEmpty());
        assertTrue(this.manager.isSecondEmpty());

        this.manager.giveFirst(B);
        this.manager.giveFirst(C);
        this.manager.giveSecond(W);
        assertEquals(B, this.intConsumed.get());
        assertEquals(W, this.doubleConsumed.get());
        this.manager.giveSecond(X);
        assertEquals(C, this.intConsumed.get());
        assertEquals(X, this.doubleConsumed.get());
        assertTrue(this.manager.isFirstEmpty());
        assertTrue(this.manager.isSecondEmpty());

        this.manager.giveFirst(D);
        this.manager.giveSecond(Y);
        assertEquals(D, this.intConsumed.get());
        assertEquals(Y, this.doubleConsumed.get());
        assertTrue(this.manager.isFirstEmpty());
        assertTrue(this.manager.isSecondEmpty());

        this.manager.giveFirst(E);
        this.manager.removeFirst(E);
        this.manager.giveFirst(E);
        this.manager.giveSecond(Z);
        assertEquals(E, this.intConsumed.get());
        assertEquals(Z, this.doubleConsumed.get());
        assertTrue(this.manager.isFirstEmpty());
        assertTrue(this.manager.isSecondEmpty());
    }

    @Test
    public void removeFirst() {
        this.manager.giveFirst(A);
        this.manager.removeFirst(A);
        assertTrue(this.manager.getFirstWaiting().isEmpty());

        this.manager.giveFirst(A);
        this.manager.giveFirst(B);
        this.manager.removeFirst(A);
        assertEquals(1, this.manager.getFirstWaiting().size());
        assertArrayEquals(new Object[]{B}, this.manager.getFirstWaiting().toArray());
    }

    @Test
    public void removeSecond() {
        this.manager.giveSecond(V);
        this.manager.removeSecond(V);
        assertTrue(this.manager.getSecondWaiting().isEmpty());

        this.manager.giveSecond(V);
        this.manager.giveSecond(W);
        this.manager.removeSecond(V);
        assertEquals(1, this.manager.getSecondWaiting().size());
        assertArrayEquals(new Object[]{W}, this.manager.getSecondWaiting().toArray());
    }
}
