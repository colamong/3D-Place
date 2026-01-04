package com.colombus.common.utility.uuid;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

public class UuidGenerator {

    private static final Random random = new Random();

    public static UUID generate() {
        long timestampMs = Instant.now().toEpochMilli();

        long mostSigBits = 0L;
        long leastSigBits = 0L;

        mostSigBits |= (timestampMs & 0xFFFFFFFFFFFFL) << 16;
        mostSigBits |= 0x7000;
        mostSigBits |= random.nextInt(1 << 12);

        leastSigBits = random.nextLong();

        return new UUID(mostSigBits, leastSigBits);
    }
}
