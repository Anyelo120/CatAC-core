package dev.catac.config;

import dev.catac.api.EnforcementMode;
import dev.catac.api.ExemptionProvider;
import dev.catac.api.PlayerMessageProvider;
import dev.catac.api.ViolationHandler;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CatACConfig {
    private final EnforcementMode enforcementMode;
    private final Map<String, CheckPolicy> policies;
    private final ViolationHandler violationHandler;
    private final ExemptionProvider exemptionProvider;
    private final Component kickMessage;
    private final PlayerMessageProvider playerMessageProvider;
    private final long playerNoticeCooldownNanos;
    private final int warningsBeforeKick;
    private final AuraDecoyPolicy auraDecoyPolicy;
    private final DamageProtectionPolicy damageProtectionPolicy;
    private final PacketFloodPolicy packetFloodPolicy;
    private final long joinGraceNanos;
    private final long teleportGraceNanos;
    private final long velocityGraceNanos;
    private final long networkProbeIntervalNanos;
    private final long networkAcknowledgementTimeoutNanos;
    private final long combatRewindPaddingNanos;
    private final long combatMaxRewindNanos;
    private final double lagCompensationThresholdMillis;
    private final boolean disconnectMalformedPackets;
    private final boolean telemetryEnabled;
    private final boolean debug;

    private CatACConfig(Builder builder) {
        this.enforcementMode = builder.enforcementMode;
        this.policies = Map.copyOf(builder.policies);
        this.violationHandler = builder.violationHandler;
        this.exemptionProvider = builder.exemptionProvider;
        this.kickMessage = builder.kickMessage;
        this.playerMessageProvider = builder.playerMessageProvider;
        this.playerNoticeCooldownNanos = builder.playerNoticeCooldown.toNanos();
        this.warningsBeforeKick = builder.warningsBeforeKick;
        this.auraDecoyPolicy = builder.auraDecoyPolicy;
        this.damageProtectionPolicy = builder.damageProtectionPolicy;
        this.packetFloodPolicy = builder.packetFloodPolicy;
        this.joinGraceNanos = builder.joinGrace.toNanos();
        this.teleportGraceNanos = builder.teleportGrace.toNanos();
        this.velocityGraceNanos = builder.velocityGrace.toNanos();
        this.networkProbeIntervalNanos = builder.networkProbeInterval.toNanos();
        this.networkAcknowledgementTimeoutNanos = builder.networkAcknowledgementTimeout.toNanos();
        this.combatRewindPaddingNanos = builder.combatRewindPadding.toNanos();
        this.combatMaxRewindNanos = builder.combatMaxRewind.toNanos();
        this.lagCompensationThresholdMillis = builder.lagCompensationThresholdMillis;
        this.disconnectMalformedPackets = builder.disconnectMalformedPackets;
        this.telemetryEnabled = builder.telemetryEnabled;
        this.debug = builder.debug;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CatACConfig defaults() {
        return builder().build();
    }

    public EnforcementMode enforcementMode() {
        return enforcementMode;
    }

    public CheckPolicy policyFor(String checkId, CheckPolicy fallback) {
        Objects.requireNonNull(checkId, "checkId");
        return policies.getOrDefault(checkId, Objects.requireNonNull(fallback, "fallback"));
    }

    public Map<String, CheckPolicy> policyOverrides() {
        return policies;
    }

    public ViolationHandler violationHandler() {
        return violationHandler;
    }

    public ExemptionProvider exemptionProvider() {
        return exemptionProvider;
    }

    public Component kickMessage() {
        return kickMessage;
    }

    /** Formats a warning or setback message; returning null suppresses it. */
    public PlayerMessageProvider playerMessageProvider() { return playerMessageProvider; }

    /** Cooldown shared by player-facing warnings and setback notices. */
    public long playerNoticeCooldownNanos() { return playerNoticeCooldownNanos; }

    /** Number of quiet warnings for the same check required before a normal kick. */
    public int warningsBeforeKick() { return warningsBeforeKick; }
    public AuraDecoyPolicy auraDecoyPolicy() { return auraDecoyPolicy; }
    public DamageProtectionPolicy damageProtectionPolicy() { return damageProtectionPolicy; }
    public PacketFloodPolicy packetFloodPolicy() { return packetFloodPolicy; }

    public long joinGraceNanos() {
        return joinGraceNanos;
    }

    public long teleportGraceNanos() {
        return teleportGraceNanos;
    }

    public long velocityGraceNanos() {
        return velocityGraceNanos;
    }

    /** Interval between low-cost server Ping probes when no velocity is pending. */
    public long networkProbeIntervalNanos() { return networkProbeIntervalNanos; }

    /** Maximum time CatAC waits for a Pong or teleport confirmation. */
    public long networkAcknowledgementTimeoutNanos() { return networkAcknowledgementTimeoutNanos; }

    /** Minimum compensation applied before measured client latency. */
    public long combatRewindPaddingNanos() { return combatRewindPaddingNanos; }

    /** Hard upper bound for a melee rewind; it must never become a reach bonus. */
    public long combatMaxRewindNanos() { return combatMaxRewindNanos; }

    public double lagCompensationThresholdMillis() {
        return lagCompensationThresholdMillis;
    }

    public boolean disconnectMalformedPackets() {
        return disconnectMalformedPackets;
    }

    /** Enables low-cost cumulative metrics exposed through {@code CatAC.metrics()}. */
    public boolean telemetryEnabled() { return telemetryEnabled; }

    public boolean debug() {
        return debug;
    }

    public static final class Builder {
        private EnforcementMode enforcementMode = EnforcementMode.SETBACK;
        private final Map<String, CheckPolicy> policies = new HashMap<>();
        private ViolationHandler violationHandler = ViolationHandler.NOOP;
        private ExemptionProvider exemptionProvider = ExemptionProvider.NONE;
        private Component kickMessage = Component.text("Unfair gameplay was detected.");
        private PlayerMessageProvider playerMessageProvider = PlayerMessageProvider.DEFAULT;
        private Duration playerNoticeCooldown = Duration.ofSeconds(4);
        private int warningsBeforeKick = 2;
        private AuraDecoyPolicy auraDecoyPolicy = AuraDecoyPolicy.defaults();
        private DamageProtectionPolicy damageProtectionPolicy = DamageProtectionPolicy.defaults();
        private PacketFloodPolicy packetFloodPolicy = PacketFloodPolicy.defaults();
        private Duration joinGrace = Duration.ofSeconds(1);
        private Duration teleportGrace = Duration.ofMillis(300);
        private Duration velocityGrace = Duration.ofMillis(750);
        private Duration networkProbeInterval = Duration.ofSeconds(1);
        private Duration networkAcknowledgementTimeout = Duration.ofSeconds(3);
        private Duration combatRewindPadding = Duration.ofMillis(50);
        private Duration combatMaxRewind = Duration.ofMillis(350);
        private double lagCompensationThresholdMillis = 80.0;
        private boolean disconnectMalformedPackets = true;
        private boolean telemetryEnabled = true;
        private boolean debug;

        private Builder() {
        }

        public Builder enforcementMode(EnforcementMode enforcementMode) {
            this.enforcementMode = Objects.requireNonNull(enforcementMode, "enforcementMode");
            return this;
        }

        public Builder policy(String checkId, CheckPolicy policy) {
            policies.put(requireCheckId(checkId), Objects.requireNonNull(policy, "policy"));
            return this;
        }

        public Builder disableCheck(String checkId) {
            policies.put(requireCheckId(checkId), CheckPolicy.DISABLED);
            return this;
        }

        public Builder violationHandler(ViolationHandler violationHandler) {
            this.violationHandler = Objects.requireNonNull(violationHandler, "violationHandler");
            return this;
        }

        public Builder exemptionProvider(ExemptionProvider exemptionProvider) {
            this.exemptionProvider = Objects.requireNonNull(exemptionProvider, "exemptionProvider");
            return this;
        }

        public Builder kickMessage(Component kickMessage) {
            this.kickMessage = Objects.requireNonNull(kickMessage, "kickMessage");
            return this;
        }

        /**
         * Configures contextual player feedback. The provider may return null
         * for checks or notice types that a host does not want to expose.
         */
        public Builder playerMessageProvider(PlayerMessageProvider playerMessageProvider) {
            this.playerMessageProvider = Objects.requireNonNull(playerMessageProvider, "playerMessageProvider");
            return this;
        }

        public Builder playerNoticeCooldown(Duration playerNoticeCooldown) {
            this.playerNoticeCooldown = requirePositive(playerNoticeCooldown, "playerNoticeCooldown");
            return this;
        }

        /** Set to zero only when the host explicitly wants immediate regular kicks. */
        public Builder warningsBeforeKick(int warningsBeforeKick) {
            if (warningsBeforeKick < 0 || warningsBeforeKick > 100) {
                throw new IllegalArgumentException("warningsBeforeKick must be between 0 and 100");
            }
            this.warningsBeforeKick = warningsBeforeKick;
            return this;
        }

        public Builder auraDecoyPolicy(AuraDecoyPolicy auraDecoyPolicy) {
            this.auraDecoyPolicy = Objects.requireNonNull(auraDecoyPolicy, "auraDecoyPolicy");
            return this;
        }

        public Builder damageProtectionPolicy(DamageProtectionPolicy damageProtectionPolicy) {
            this.damageProtectionPolicy = Objects.requireNonNull(damageProtectionPolicy, "damageProtectionPolicy");
            return this;
        }

        public Builder packetFloodPolicy(PacketFloodPolicy packetFloodPolicy) {
            this.packetFloodPolicy = Objects.requireNonNull(packetFloodPolicy, "packetFloodPolicy");
            return this;
        }

        public Builder joinGrace(Duration joinGrace) {
            this.joinGrace = requireNonNegative(joinGrace, "joinGrace");
            return this;
        }

        public Builder teleportGrace(Duration teleportGrace) {
            this.teleportGrace = requireNonNegative(teleportGrace, "teleportGrace");
            return this;
        }

        public Builder velocityGrace(Duration velocityGrace) {
            this.velocityGrace = requireNonNegative(velocityGrace, "velocityGrace");
            return this;
        }

        public Builder networkProbeInterval(Duration networkProbeInterval) {
            this.networkProbeInterval = requirePositive(networkProbeInterval, "networkProbeInterval");
            return this;
        }

        public Builder networkAcknowledgementTimeout(Duration networkAcknowledgementTimeout) {
            this.networkAcknowledgementTimeout = requirePositive(networkAcknowledgementTimeout,
                    "networkAcknowledgementTimeout");
            return this;
        }

        public Builder combatRewindPadding(Duration combatRewindPadding) {
            this.combatRewindPadding = requireNonNegative(combatRewindPadding, "combatRewindPadding");
            return this;
        }

        public Builder combatMaxRewind(Duration combatMaxRewind) {
            this.combatMaxRewind = requirePositive(combatMaxRewind, "combatMaxRewind");
            return this;
        }

        public Builder lagCompensationThresholdMillis(double threshold) {
            if (!Double.isFinite(threshold) || threshold < 50.0) {
                throw new IllegalArgumentException("lag threshold must be finite and >= 50 ms");
            }
            this.lagCompensationThresholdMillis = threshold;
            return this;
        }

        public Builder disconnectMalformedPackets(boolean disconnectMalformedPackets) {
            this.disconnectMalformedPackets = disconnectMalformedPackets;
            return this;
        }

        public Builder telemetryEnabled(boolean telemetryEnabled) {
            this.telemetryEnabled = telemetryEnabled;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public CatACConfig build() {
            if (combatRewindPadding.compareTo(combatMaxRewind) > 0) {
                throw new IllegalArgumentException("combatRewindPadding cannot exceed combatMaxRewind");
            }
            return new CatACConfig(this);
        }

        private static String requireCheckId(String checkId) {
            Objects.requireNonNull(checkId, "checkId");
            if (!checkId.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*")) {
                throw new IllegalArgumentException("Invalid check id: " + checkId);
            }
            return checkId;
        }

        private static Duration requireNonNegative(Duration duration, String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isNegative()) {
                throw new IllegalArgumentException(name + " cannot be negative");
            }
            return duration;
        }

        private static Duration requirePositive(Duration duration, String name) {
            requireNonNegative(duration, name);
            if (duration.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
