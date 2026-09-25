package dev.catac.engine;

import dev.catac.api.CatViolationEvent;
import dev.catac.api.CheckDescriptor;
import dev.catac.api.EnforcementMode;
import dev.catac.api.PlayerNotice;
import dev.catac.api.PlayerNoticeType;
import dev.catac.api.ViolationAction;
import dev.catac.check.CheckResult;
import dev.catac.config.CatACConfig;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.EnforcementDecision;
import dev.catac.state.PlayerData;
import dev.catac.state.ViolationState;
import net.minestom.server.MinecraftServer;

import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

public final class EnforcementEngine {
    private static final System.Logger LOGGER = System.getLogger(EnforcementEngine.class.getName());

    private final CatACConfig config;
    private final EnforcementMetrics metrics = new EnforcementMetrics();

    public EnforcementEngine(CatACConfig config) {
        this.config = config;
    }

    public EnforcementDecision handle(PlayerData data, int slot, CheckDescriptor descriptor,
                                      CheckPolicy policy, CheckResult result, long nowNanos) {
        ViolationState state = data.violation(slot);
        if (result.passed()) {
            state.decay(policy.decayPerPass());
            return EnforcementDecision.NONE;
        }

        if (config.telemetryEnabled()) {
            metrics.violationSample();
        }

        double buffer = state.add(result.severity());
        boolean malformedDisconnect = result.disconnectRecommended() && config.disconnectMalformedPackets();
        boolean enforcementEnabled = config.enforcementMode() != EnforcementMode.MONITOR;

        ViolationAction action = ViolationAction.ALERT;
        boolean cancel = false;
        boolean setback = false;
        boolean kick = false;

        if (malformedDisconnect) {
            action = ViolationAction.KICK;
            cancel = true;
            kick = true;
        } else if (enforcementEnabled && config.enforcementMode() == EnforcementMode.KICK &&
                buffer >= policy.kickBuffer() && state.playerWarnings() >= config.warningsBeforeKick()) {
            action = ViolationAction.KICK;
            cancel = true;
            kick = true;
        } else if (enforcementEnabled && descriptor.setbackEligible() && buffer >= policy.setbackBuffer()) {
            action = ViolationAction.SETBACK;
            cancel = true;
            setback = true;
        } else if (enforcementEnabled && result.cancelImmediately()) {
            action = ViolationAction.CANCEL_PACKET;
            cancel = true;
        }

        boolean reachedAlertThreshold = buffer >= policy.alertBuffer();
        long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(policy.alertCooldownMillis());
        boolean shouldNotify = kick || (reachedAlertThreshold && state.canAlert(nowNanos, cooldownNanos));
        if (shouldNotify) {
            if (config.telemetryEnabled()) {
                metrics.alert();
            }
            CatViolationEvent event = new CatViolationEvent(
                    data.player(), descriptor, result.severity(), buffer, result.evidence(), action);
            try {
                MinecraftServer.getGlobalEventHandler().call(event);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "CatAC violation event listener failed", exception);
            }
            try {
                config.violationHandler().onViolation(event);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "CatAC violation handler failed", exception);
            }
            if (config.debug()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        descriptor.id() + " player=" + data.player().getUsername() +
                                " buffer=" + buffer + " evidence=" + result.evidence());
            }
        }

        // Invalid protocol data is disconnected immediately. All normal checks
        // are deliberately warned and rate-limited before they can kick.
        if (!kick && reachedAlertThreshold && state.canNotifyPlayer(nowNanos, config.playerNoticeCooldownNanos())) {
            PlayerNoticeType type = setback ? PlayerNoticeType.SETBACK : PlayerNoticeType.WARNING;
            PlayerNotice notice = new PlayerNotice(data.player(), descriptor, type, result.severity(), buffer,
                    state.playerWarnings());
            try {
                Component message = config.playerMessageProvider().message(notice);
                if (message != null) {
                    data.player().sendMessage(message);
                }
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.ERROR, "CatAC player message provider failed", exception);
            }
        }

        if (config.telemetryEnabled()) {
            metrics.decision(cancel, setback, kick);
        }

        return new EnforcementDecision(cancel, setback, kick, action);
    }

    EnforcementMetrics metrics() { return metrics; }

    /** Records a fully armed private-decoy hit; the global mode still governs kick. */
    public boolean handleAuraConfirmation(PlayerData data, CheckDescriptor descriptor, String evidence) {
        boolean kick = config.enforcementMode() == EnforcementMode.KICK;
        ViolationAction action = kick ? ViolationAction.KICK : ViolationAction.ALERT;
        if (config.telemetryEnabled()) {
            metrics.violationSample();
            metrics.alert();
            metrics.decision(kick, false, kick);
        }
        CatViolationEvent event = new CatViolationEvent(data.player(), descriptor, 10.0, 10.0, evidence, action);
        try {
            MinecraftServer.getGlobalEventHandler().call(event);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "CatAC violation event listener failed", exception);
        }
        try {
            config.violationHandler().onViolation(event);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "CatAC violation handler failed", exception);
        }
        return kick;
    }
}
