package com.artillexstudios.axsellwands.utils.bucket;

import java.util.Comparator;

public final class PartitioningStrategies {
    private PartitioningStrategies() {
    }

    public static <T> PartitioningStrategy<T> lowestSize() {
        return partitions -> partitions.stream()
                .min(Comparator.comparingInt(BucketPartition::size))
                .orElseThrow();
    }
}
