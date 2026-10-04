package com.executionpilot.action.retry;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import org.springframework.stereotype.Component;

@Component
public class ExponentialBackoffRetryStrategy implements RetryStrategy {

    @Override
    public boolean supports(RetryBackoffType backoffType) {
        return backoffType == RetryBackoffType.EXPONENTIAL;
    }

    @Override
    public long computeDelayMillis(RetryPolicy retryPolicy, int failedAttemptNo) {
        long initialDelayMillis = retryPolicy.getInitialDelay().toMillis();
        long computedDelay = (long) (initialDelayMillis * Math.pow(2, failedAttemptNo - 1));
        return Math.min(computedDelay, retryPolicy.getMaxDelay().toMillis());
    }
}
