package dev.catac.testing;

@FunctionalInterface
public interface ReplayExecutor<T, R> {
    R execute(T payload, long nowNanos);
}
