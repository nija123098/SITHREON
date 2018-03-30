package com.nija123098.sithreon.backend.util;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * A resource manager for pairs of resources.
 *
 * When either one of one resource is given to the manager,
 * if the other type is available the other will be removed
 * and the task assigned to the manager will be executed
 * with the removed other type instance and the given instance.
 * If no other type is available then it will be put in a wait
 * list to be used with the next other type object given.
 *
 * @param <F> the first type to manage.
 * @param <S> the second type to manage.
 */
public class DualPriorityResourceManager<F, S> {
    /**
     * The task to run when a pair is found.
     */
    private final BiConsumer<F, S> task;

    /**
     * The list of waiting first resources.
     */
    private final Queue<F> firstWaiting = new PriorityQueue<>();

    /**
     * The list of waiting second resources.
     */
    private final Queue<S> secondWaiting = new PriorityQueue<>();

    /**
     * Constructs a manager with the specified task.
     *
     * @param task the task to run for each pair of resources.
     */
    public DualPriorityResourceManager(BiConsumer<F, S> task) {
        this.task = task;
    }

    /**
     * Provides {@link F} resource to pair with a {@link S} instance for the task.
     *
     * @param f a {@link F} resource to make available.
     * @return the {@link S} resource paired with it, or null if none is currently available.
     */
    public synchronized S giveFirst(F f){
        if (this.secondWaiting.isEmpty()) this.firstWaiting.add(f);
        else {
            S s = secondWaiting.poll();
            this.task.accept(f, s);
            return s;
        }
        return null;
    }

    /**
     * Provides {@link S} resource to pair with a {@link F} instance for the task.
     *
     * @param s a {@link S} resource to make available.
     * @return the {@link F} resource paired with it, or null if none is currently available.
     */
    public synchronized F giveSecond(S s){
        if (this.firstWaiting.isEmpty()) this.secondWaiting.add(s);
        else {
            F f = this.firstWaiting.poll();
            this.task.accept(f, s);
            return f;
        }
        return null;
    }

    /**
     * Gets the list of waiting first type instances.
     *
     * @return the list of waiting first type instances.
     */
    public Collection<F> getFirstWaiting() {
        return this.firstWaiting;
    }

    /**
     * Gets the list of waiting second type instances.
     *
     * @return the list of waiting second type instances.
     */
    public Collection<S> getSecondWaiting() {
        return this.secondWaiting;
    }
}
