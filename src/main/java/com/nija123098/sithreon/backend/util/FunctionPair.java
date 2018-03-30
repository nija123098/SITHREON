package com.nija123098.sithreon.backend.util;

import javafx.util.Pair;

import java.util.function.Function;

public class FunctionPair<K, V> extends Pair<K, V> {
    public FunctionPair(K key, Function<K, V> function) {
        super(key, function.apply(key));
    }
}
