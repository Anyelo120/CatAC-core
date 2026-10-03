package dev.catac.api;

/** Set by trusted host metadata, never by client brand or self-reported payloads. */
public enum ClientProfile {
    JAVA_1_21_11,
    TRANSLATED,
    CUSTOM,
    UNKNOWN
}
