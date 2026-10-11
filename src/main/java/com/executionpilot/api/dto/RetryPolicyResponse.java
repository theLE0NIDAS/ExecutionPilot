package com.executionpilot.api.dto;

import com.executionpilot.action.definition.RetryBackoffType;

public record RetryPolicyResponse(
        int maxAttempts,
        long initialDelayMillis,
        long maxDelayMillis,
        RetryBackoffType backoffType
) {
}
