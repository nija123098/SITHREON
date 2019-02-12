package com.nija123098.sithreon.backend.util;

import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BiConsumer;

/**
 * A resource manager for pairs of resources.
 * <p>
 * When either one of one resource is given to the manager,
 * if the other type is available the other will be removed
 * and the task assigned to the manager will be executed
 * with the removed other type instance and the given instance.
 * <p>
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
    public S giveFirst(F f) {
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
    public F giveSecond(S s) {
        if (this.firstWaiting.isEmpty()) this.secondWaiting.add(s);
        else {
            F f = this.firstWaiting.poll();
            this.task.accept(f, s);
            return f;
        }
        return null;
    }

    /**
     * Removes the {@link F} resource if it is in the {@code firstWaiting} queue.
     *
     * @param f the resource to remove.
     * @return if the resource was found in the queue.
     */
    public boolean removeFirst(F f) {
        return this.firstWaiting.remove(f);
    }

    /**
     * Removes the {@link S} resource if it is in the {@code secondWaiting} queue.
     *
     * @param s the resource to remove.
     * @return if the resource was found in the queue.
     */
    public boolean removeSecond(S s) {
        return this.secondWaiting.remove(s);
    }

    /**
     * Gets the list of waiting first type instances.
     * <p>
     * Returns the list it's self, not a copy.
     *
     * @return the list of waiting first type instances.
     */
    public Collection<F> getFirstWaiting() {
        return this.firstWaiting;
    }

    /**
     * Gets the list of waiting second type instances.
     * <p>
     * Returns the list it's self, not a copy.
     *
     * @return the list of waiting second type instances.
     */
    public Collection<S> getSecondWaiting() {
        return this.secondWaiting;
    }

    /**
     * Gets if there are no primary resources in queue.
     *
     * @return if there are no primary resources in queue.
     */
    public boolean isFirstEmpty() {
        return this.firstWaiting.isEmpty();
    }

    /**
     * Gets if there are no secondary resources in queue.
     *
     * @return if there are no secondary resources in queue.
     */
    public boolean isSecondEmpty() {
        return this.secondWaiting.isEmpty();
    }

    /**
     * Clears all waiting queues.
     */
    public void clear() {
        this.firstWaiting.clear();
        this.secondWaiting.clear();
    }
}
