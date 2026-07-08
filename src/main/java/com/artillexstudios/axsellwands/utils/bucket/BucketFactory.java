package com.artillexstudios.axsellwands.utils.bucket;

public final class BucketFactory {
    private BucketFactory() {
    }

    public static <T> Bucket<T> newConcurrentBucket(int partitionCount, PartitioningStrategy<T> strategy) {
        return new Bucket<>(partitionCount, strategy);
    }
}
