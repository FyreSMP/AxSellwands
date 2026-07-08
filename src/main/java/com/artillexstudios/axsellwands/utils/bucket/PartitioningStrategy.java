package com.artillexstudios.axsellwands.utils.bucket;

import java.util.List;

@FunctionalInterface
public interface PartitioningStrategy<T> {
    BucketPartition<T> select(List<BucketPartition<T>> partitions);
}
