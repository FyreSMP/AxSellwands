package com.artillexstudios.axsellwands.utils.bucket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Bucket<T> {
    private final List<BucketPartition<T>> partitions;
    private final PartitioningStrategy<T> strategy;
    private final Map<T, BucketPartition<T>> membership = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger(0);

    Bucket(int partitionCount, PartitioningStrategy<T> strategy) {
        this.strategy = strategy;
        this.partitions = new ArrayList<>(partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            partitions.add(new BucketPartition<>());
        }
    }

    public void add(T element) {
        if (membership.containsKey(element)) return;
        BucketPartition<T> partition = strategy.select(partitions);
        partition.add(element);
        membership.put(element, partition);
    }

    public void addAll(Collection<T> elements) {
        for (T element : elements) add(element);
    }

    public void remove(T element) {
        BucketPartition<T> partition = membership.remove(element);
        if (partition != null) partition.remove(element);
    }

    public int partitionCount() {
        return partitions.size();
    }

    /**
     * Returns a cursor that round-robins through this bucket's partitions.
     * The position is shared on the bucket, so repeated {@code asCycle().next()}
     * calls (e.g. once per scheduler tick) keep advancing instead of restarting.
     */
    public Iterator<BucketPartition<T>> asCycle() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !partitions.isEmpty();
            }

            @Override
            public BucketPartition<T> next() {
                if (partitions.isEmpty()) throw new NoSuchElementException();
                int index = Math.floorMod(cursor.getAndIncrement(), partitions.size());
                return partitions.get(index);
            }
        };
    }
}
