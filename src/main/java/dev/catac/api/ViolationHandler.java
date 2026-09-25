package dev.catac.api;

@FunctionalInterface
public interface ViolationHandler {
    ViolationHandler NOOP = event -> { };

    void onViolation(CatViolationEvent event);
}
