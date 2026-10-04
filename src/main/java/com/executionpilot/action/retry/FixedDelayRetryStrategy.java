package com.executionpilot.action.retry;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import org.springframework.stereotype.Component;

@Component
public class FixedDelayRetryStrategy implements RetryStrategy {

    @Override
    public boolean supports(RetryBackoffType backoffType) {
        return backoffType == RetryBackoffType.FIXED_DELAY;
    }

    @Override
    public long computeDelayMillis(RetryPolicy retryPolicy, int failedAttemptNo) {
        return Math.min(retryPolicy.getInitialDelay().toMillis(), retryPolicy.getMaxDelay().toMillis());
    }
}
