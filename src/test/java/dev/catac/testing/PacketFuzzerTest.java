package dev.catac.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketFuzzerTest {
    @Test
    void isSeedDeterministicAndIncludesHostileBoundaries() {
        List<PacketFuzzCase> first = PacketFuzzer.generate(42L, 20);
        assertEquals(first, PacketFuzzer.generate(42L, 20));
        assertTrue(first.stream().anyMatch(PacketFuzzCase::hasUnsafeCoordinate));
        assertTrue(first.stream().anyMatch(PacketFuzzCase::hasUnsafeCursor));
    }
}
