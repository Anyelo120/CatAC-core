package dev.catac.testing;

import java.util.Objects;

/**
 * One independently labeled session/trial; do not label correlated packets as independent trials.
 */
public record LabeledTrial(String checkId, Label label, boolean detected) {
    public LabeledTrial {
        Objects.requireNonNull(checkId);
        Objects.requireNonNull(label);
    }

    public enum Label {
        LEGITIMATE,
        CHEAT,
        UNKNOWN
    }
}
