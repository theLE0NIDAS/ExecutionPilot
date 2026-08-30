package com.executionpilot.workflow.builder;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;

import java.time.Duration;

public final class RetryPolicyBuilder {

    private int maxAttempts = 1;
    private Duration initialDelay = Duration.ZERO;
    private Duration maxDelay = Duration.ZERO;
    private RetryBackoffType backoffType = RetryBackoffType.FIXED_DELAY;

    public RetryPolicyBuilder maxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public RetryPolicyBuilder initialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
        return this;
    }

    public RetryPolicyBuilder maxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
        return this;
    }

    public RetryPolicyBuilder backoffType(RetryBackoffType backoffType) {
        this.backoffType = backoffType;
        return this;
    }

    public RetryPolicy build() {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, backoffType);
    }
}
