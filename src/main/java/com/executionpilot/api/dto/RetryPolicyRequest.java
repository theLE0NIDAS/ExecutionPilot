package com.executionpilot.api.dto;

import com.executionpilot.action.definition.RetryBackoffType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RetryPolicyRequest(
        @Min(1) int maxAttempts,
        @NotNull Long initialDelayMillis,
        @NotNull Long maxDelayMillis,
        @NotNull RetryBackoffType backoffType
) {
    public RetryPolicyRequest {
        if (initialDelayMillis < 0) {
            throw new IllegalArgumentException("initialDelayMillis must be non-negative");
        }
        if (maxDelayMillis < 0) {
            throw new IllegalArgumentException("maxDelayMillis must be non-negative");
        }
        if (maxDelayMillis < initialDelayMillis) {
            throw new IllegalArgumentException("maxDelayMillis must be greater than or equal to initialDelayMillis");
        }
    }
}
