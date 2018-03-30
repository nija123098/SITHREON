package com.nija123098.sithreon.backend.util;

import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.Repository;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * An enum representing priority for use in {@link Match#compareTo(Match)}.
 */
public enum PriorityLevel {
    /** The highest priority for important special requests. */
    ULTRA_HIGH,
    /** A high priority for special requests. */
    VERY_HIGH,
    /** The highest naturally earned priority. */
    HIGH,
    /** The priority for normal execution. */
    MEDIUM,
    /** The lowest natural earned priority. */
    LOW,
    /** The low priority for partially disabling the {@link Repository}. */
    VERY_LOW,
    /** The lowest priority for specially unimportant {@link Repository} instances. */
    ULTRA_LOW,;
    private static class Stuff extends Pair<PriorityLevel, PriorityLevel> implements Comparable<Stuff> {
        public Stuff(PriorityLevel key, PriorityLevel value) {
            super(key, value);
        }

        private int pr(){
            return Math.min(this.getKey().ordinal(), this.getValue().ordinal());
        }

        private int pra(){
            return Math.max(this.getKey().ordinal(), this.getValue().ordinal());
        }

        @Override
        public int compareTo(Stuff o) {
            if (this.pr() != o.pr()) return o.pr() - this.pr();// ? 1 : -1;
            return o.pra() - this.pra();// ? 1 : -1;
        }

        @Override
        public String toString() {
            return this.getKey() + "-" + this.getValue();
        }
    }

    public static void main(String[] args) {
        ArrayList<Stuff> stuffs = new ArrayList<>();
        for (int i = 0; i < values().length; i++) {
            for (int j = 0; j < i + 1; j++) {
                stuffs.add(new Stuff(values()[i], values()[j]));
            }
        }
        stuffs.sort(Comparator.naturalOrder());
        stuffs.forEach(System.out::println);
    }
}
