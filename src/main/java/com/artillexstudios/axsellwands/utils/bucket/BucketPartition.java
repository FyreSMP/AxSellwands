package com.artillexstudios.axsellwands.utils.bucket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BucketPartition<T> {
    private final Set<T> elements = ConcurrentHashMap.newKeySet();

    boolean add(T element) {
        return elements.add(element);
    }

    boolean remove(T element) {
        return elements.remove(element);
    }

    public int size() {
        return elements.size();
    }

    public void each(Consumer<T> consumer) {
        for (T element : elements) {
            consumer.accept(element);
        }
    }
}
