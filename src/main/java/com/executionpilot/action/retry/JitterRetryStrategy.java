package com.executionpilot.action.retry;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class JitterRetryStrategy implements RetryStrategy {

    @Override
    public boolean supports(RetryBackoffType backoffType) {
        return backoffType == RetryBackoffType.JITTER;
    }

    @Override
    public long computeDelayMillis(RetryPolicy retryPolicy, int failedAttemptNo) {
        long initialDelayMillis = retryPolicy.getInitialDelay().toMillis();
        long exponentialBase = (long) (initialDelayMillis * Math.pow(2, failedAttemptNo - 1));
        long jitterBound = Math.max(initialDelayMillis, 1L);
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        return Math.min(exponentialBase + jitter, retryPolicy.getMaxDelay().toMillis());
    }
}
