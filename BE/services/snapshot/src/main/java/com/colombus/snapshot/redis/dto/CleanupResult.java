package com.colombus.snapshot.redis.dto;

public record CleanupResult(
        long opIdsRemoved,
        long deltasRemoved,
        long tombstonesRemoved
) {
    @Override
    public String toString() {
        return String.format("op_ids=%d, deltas=%d, tombstones=%d",
                opIdsRemoved, deltasRemoved, tombstonesRemoved);
    }
}
