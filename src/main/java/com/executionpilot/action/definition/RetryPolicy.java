package com.executionpilot.action.definition;

import java.time.Duration;
import java.util.Objects;

public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final RetryBackoffType backoffType;

    public RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay, RetryBackoffType backoffType) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        this.initialDelay = requireDuration(initialDelay, "initialDelay");
        this.maxDelay = requireDuration(maxDelay, "maxDelay");
        if (this.maxDelay.compareTo(this.initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to initialDelay");
        }

        this.maxAttempts = maxAttempts;
        this.backoffType = Objects.requireNonNull(backoffType, "backoffType must not be null");
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public RetryBackoffType getBackoffType() {
        return backoffType;
    }

    private static Duration requireDuration(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }
}
