package dev.catac.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Reproducible primitive input generator for adapter and parser boundary tests. */
public final class PacketFuzzer {
    private PacketFuzzer() {
    }

    public static List<PacketFuzzCase> generate(long seed, int count) {
        if (count < 1 || count > 100_000) throw new IllegalArgumentException("count must be in [1,100000]");
        SplittableRandom random = new SplittableRandom(seed);
        List<PacketFuzzCase> cases = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int mode = index % 5;
            double x = mode == 0 ? Double.NaN : random.nextDouble(-31_000_000.0, 31_000_000.0);
            double y = mode == 1 ? Double.POSITIVE_INFINITY : random.nextDouble(-2_100.0, 2_100.0);
            double z = mode == 2 ? Double.NEGATIVE_INFINITY : random.nextDouble(-31_000_000.0, 31_000_000.0);
            float cursorX = mode == 3 ? Float.NaN : (float) random.nextDouble(-0.25, 1.25);
            float cursorY = mode == 4 ? Float.POSITIVE_INFINITY : (float) random.nextDouble(-0.25, 1.25);
            float cursorZ = (float) random.nextDouble(-0.25, 1.25);
            cases.add(new PacketFuzzCase(x, y, z, cursorX, cursorY, cursorZ,
                    random.nextInt(-16, 144), (short) random.nextInt(-1_200, 1_200)));
        }
        return List.copyOf(cases);
    }
}
