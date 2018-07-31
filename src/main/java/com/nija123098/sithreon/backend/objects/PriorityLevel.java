package com.nija123098.sithreon.backend.objects;

/**
 * An enum representing priority for use in {@link Match#compareTo(Match)}.
 */
public enum PriorityLevel {
    /**
     * The highest priority for important special requests.
     */
    ULTRA_HIGH,
    /**
     * A high priority for special requests.
     */
    VERY_HIGH,
    /**
     * The highest naturally earned priority.
     */
    HIGH,
    /**
     * The priority for normal execution.
     */
    MEDIUM,
    /**
     * The lowest natural earned priority.
     */
    LOW,
    /**
     * The low priority for partially disabling the {@link Repository}.
     */
    VERY_LOW,
    /**
     * The lowest priority for specially unimportant {@link Repository} instances.
     */
    ULTRA_LOW,;
}
