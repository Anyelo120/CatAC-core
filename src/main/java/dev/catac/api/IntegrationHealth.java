package dev.catac.api;

/**
 * Integrations use conservative fallbacks when a provider fails; inspect checks separately via
 * diagnostics().
 */
public record IntegrationHealth(
        boolean exemptionProvider,
        boolean packetClassifier,
        boolean floodHandler,
        boolean damageProvider,
        long outboundOverflows,
        long callbackFaults) {
    public boolean healthy() {
        return exemptionProvider
                && packetClassifier
                && floodHandler
                && damageProvider
                && outboundOverflows == 0
                && callbackFaults == 0;
    }
}
