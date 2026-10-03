package dev.catac.regression;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.api.EnforcementMode;
import dev.catac.support.MinestomFixture;
import dev.catac.testing.MinestomReplayHarness;
import dev.catac.testing.ReplayFrame;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

/**
 * Timing report, not a flaky performance gate. Includes native listeners and in-memory transport.
 */
class NativePipelineBenchmarkTest {
    @Test
    void measuresTheRealNativePipelineAndKeepsOrdinaryMovementClean() throws Exception {
        try (var f = new MinestomFixture();
                var replay =
                        new MinestomReplayHarness(
                                f.player,
                                f.clock,
                                f.config().enforcementMode(EnforcementMode.MONITOR).build())) {
            int warmup = 500, count = 3000;
            long[] timings = new long[count];
            for (int i = 0; i < warmup + count; i++) {
                double angle = (i + 1) * .04;
                ClientPacket packet =
                        new ClientPlayerPositionPacket(
                                new Pos(
                                        .5 + 4 * Math.sin(angle),
                                        1,
                                        .5 + 4 * (1 - Math.cos(angle))),
                                true,
                                false);
                long stamp = f.clock.advanceNanos(50_000_000L), start = System.nanoTime();
                replay.run(List.of(new ReplayFrame<>(i, stamp, packet)));
                long elapsed = System.nanoTime() - start;
                if (i >= warmup) timings[i - warmup] = elapsed;
            }
            assertEquals(0, replay.anticheat().metrics().violationSamples());
            assertTrue(replay.anticheat().diagnostics().stream().allMatch(d -> d.faults() == 0));
            Arrays.sort(timings);
            double mean = Arrays.stream(timings).average().orElseThrow();
            String json =
                    String.format(
                            Locale.ROOT,
                            "{\n"
                                + "  \"scope\": \"single player; native listeners; in-memory"
                                + " transport; flat floor\",\n"
                                + "  \"warmup\": %d,\n"
                                + "  \"samples\": %d,\n"
                                + "  \"meanNanos\": %.0f,\n"
                                + "  \"p50Nanos\": %d,\n"
                                + "  \"p95Nanos\": %d,\n"
                                + "  \"p99Nanos\": %d,\n"
                                + "  \"maxNanos\": %d\n"
                                + "}\n",
                            warmup,
                            count,
                            mean,
                            timings[count / 2],
                            timings[(int) (count * .95)],
                            timings[(int) (count * .99)],
                            timings[count - 1]);
            Files.writeString(Path.of("target/benchmark.json"), json);
        }
    }
}
