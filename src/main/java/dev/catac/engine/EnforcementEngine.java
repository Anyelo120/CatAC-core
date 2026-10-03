package dev.catac.engine;

import dev.catac.api.*;
import dev.catac.check.CheckResult;
import dev.catac.config.CatACConfig;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.EnforcementDecision;
import dev.catac.state.PlayerData;
import dev.catac.state.ViolationState;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;

import java.util.concurrent.TimeUnit;

public final class EnforcementEngine {
    private final dev.catac.internal.CallbackFaults callbackFaults =
            new dev.catac.internal.CallbackFaults();

    public long callbackFaults() {
        return callbackFaults.count();
    }

    private final CatACConfig config;
    private final EnforcementMetrics metrics = new EnforcementMetrics();

    public EnforcementEngine(CatACConfig config) {
        this.config = config;
    }

    public EnforcementDecision handle(
            PlayerData data,
            int slot,
            CheckDescriptor descriptor,
            CheckPolicy policy,
            CheckResult result,
            long now) {
        ViolationState state = data.violation(slot);
        state.advance(now, policy.decayPerSecond(), config.incidentWindowNanos());
        if (!result.failed()) {
            if (result.cancelImmediately() && descriptor.capabilities().cancelAction())
                return new EnforcementDecision(true, false, false, ViolationAction.CANCEL_PACKET);
            return EnforcementDecision.NONE;
        }
        if (config.telemetryEnabled()) metrics.violationSample();
        double buffer = state.add(result.severity(), now);
        var caps = descriptor.capabilities();
        EnforcementMode mode = config.modeFor(descriptor.id());
        if (data.clientProfile() != ClientProfile.JAVA_1_21_11 && !caps.hardening())
            mode = EnforcementMode.MONITOR;
        boolean enforcing = mode != EnforcementMode.MONITOR;
        boolean malformed = result.outcome() == CheckResult.Outcome.MALFORMED && caps.hardening();
        boolean kick =
                malformed
                        && result.disconnectRecommended()
                        && config.disconnectMalformedPackets()
                        && caps.kick();
        if (!kick
                && enforcing
                && mode == EnforcementMode.KICK
                && caps.kick()
                && buffer >= policy.kickBuffer()
                && state.totalDetections() >= config.minimumDetectionsBeforeKick()
                && state.playerWarnings() >= config.warningsBeforeKick()) kick = true;
        boolean setback =
                !kick
                        && enforcing
                        && caps.correctMovement()
                        && buffer >= policy.setbackBuffer()
                        && data.hasSafePosition();
        boolean cancel =
                kick
                        || setback
                        || (malformed && caps.cancelAction())
                        || (enforcing && caps.cancelAction() && result.cancelImmediately());
        ViolationAction action =
                kick
                        ? ViolationAction.KICK
                        : setback
                                ? ViolationAction.SETBACK
                                : cancel ? ViolationAction.CANCEL_PACKET : ViolationAction.ALERT;
        boolean alert = buffer >= policy.alertBuffer();
        if (kick
                || (alert
                        && state.canAlert(
                                now,
                                TimeUnit.MILLISECONDS.toNanos(policy.alertCooldownMillis())))) {
            if (config.telemetryEnabled()) metrics.alert();
            CatViolationEvent event =
                    new CatViolationEvent(
                            data.player(),
                            descriptor,
                            result.severity(),
                            buffer,
                            result.evidence(),
                            action,
                            result.details());
            try {
                MinecraftServer.getGlobalEventHandler().call(event);
            } catch (RuntimeException ex) {
                callbackFaults.report(
                        dev.catac.internal.CallbackFaults.Kind.VIOLATION_EVENT, now, ex);
            }
            try {
                config.violationHandler().onViolation(event);
            } catch (RuntimeException ex) {
                callbackFaults.report(
                        dev.catac.internal.CallbackFaults.Kind.VIOLATION_HANDLER, now, ex);
            }
        }
        if (enforcing
                && !kick
                && caps.cancelAction()
                && alert
                && state.canNotifyPlayer(now, config.playerNoticeCooldownNanos())) {
            try {
                Component message =
                        config.playerMessageProvider()
                                .message(
                                        new PlayerNotice(
                                                data.player(),
                                                descriptor,
                                                setback
                                                        ? PlayerNoticeType.SETBACK
                                                        : PlayerNoticeType.WARNING,
                                                result.severity(),
                                                buffer,
                                                state.playerWarnings() + 1));
                if (message != null) {
                    data.player().sendMessage(message);
                    state.warningSent();
                }
            } catch (RuntimeException ex) {
                callbackFaults.report(
                        dev.catac.internal.CallbackFaults.Kind.PLAYER_NOTICE, now, ex);
            }
        }
        if (data.traces().enabled())
            data.traces()
                    .add(
                            new DetectionTrace(
                                    data.movementSequence(),
                                    now,
                                    descriptor.id(),
                                    result.outcome(),
                                    result.severity(),
                                    buffer,
                                    result.details(),
                                    action));
        return new EnforcementDecision(cancel, setback, kick, action);
    }

    /** Call only after applying the action successfully. */
    public void applied(boolean cancel, boolean setback, boolean kick) {
        if (config.telemetryEnabled()) metrics.decision(cancel, setback, kick);
    }

    EnforcementMetrics metrics() {
        return metrics;
    }
}
