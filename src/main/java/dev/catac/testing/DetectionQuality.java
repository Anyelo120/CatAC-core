package dev.catac.testing;

/**
 * Classification quality over externally labeled independent trials, never over just emitted
 * violations.
 */
public record DetectionQuality(
        String checkId,
        long truePositives,
        long falsePositives,
        long trueNegatives,
        long falseNegatives,
        long unknown) {
    public double precision() {
        return ratio(truePositives, truePositives + falsePositives);
    }

    public double recall() {
        return ratio(truePositives, truePositives + falseNegatives);
    }

    public double falsePositiveRate() {
        return ratio(falsePositives, falsePositives + trueNegatives);
    }

    public double legitimateUpper95Wilson() {
        double n = falsePositives + trueNegatives;
        if (n == 0) return Double.NaN;
        double z = 1.959963984540054, p = falsePositives / n, z2 = z * z;
        return (p + z2 / (2 * n) + z * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)))
                / (1 + z2 / n);
    }

    private static double ratio(long a, long n) {
        return n == 0 ? Double.NaN : (double) a / n;
    }
}
