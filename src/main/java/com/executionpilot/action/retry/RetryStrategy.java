package com.executionpilot.action.retry;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;

public interface RetryStrategy {

    boolean supports(RetryBackoffType backoffType);

    long computeDelayMillis(RetryPolicy retryPolicy, int failedAttemptNo);
}
