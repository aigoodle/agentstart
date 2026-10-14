package io.aigoodle.knowledge.service;

/** Change to a dataset's materialized document and segment counters. */
public record DatasetCountChange(int documents, int segments) {

    public static DatasetCountChange documentAdded(int segmentCount) {
        return new DatasetCountChange(1, segmentCount);
    }

    public static DatasetCountChange documentRemoved(int segmentCount) {
        return new DatasetCountChange(-1, -segmentCount);
    }

    public static DatasetCountChange segmentAdded() {
        return new DatasetCountChange(0, 1);
    }
}
