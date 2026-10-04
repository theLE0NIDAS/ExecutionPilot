package com.executionpilot.action.executor.decorator;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries the delegate executor according to the action's RetryPolicy.
 * Supports FIXED, LINEAR, and EXPONENTIAL backoff strategies.
 * Pattern: Decorator + Strategy (backoff calculation per RetryBackoffType)
 */
public class RetryActionExecutorDecorator extends ActionExecutorDecorator {

    private static final Logger log = LoggerFactory.getLogger(RetryActionExecutorDecorator.class);

    public RetryActionExecutorDecorator(ActionExecutor delegate) {
        super(delegate);
    }

    @Override
    public ActionResult execute(ActionContext context) {
        RetryPolicy policy = context.getActionDefinition().getRetryPolicy();
        int maxAttempts = policy.getMaxAttempts();
        String actionId = context.getActionDefinition().getActionId();

        ActionResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResult = delegate.execute(context);

            if (lastResult.isSuccess()) {
                if (attempt > 1) {
                    log.info("Action '{}' succeeded on attempt {}/{}", actionId, attempt, maxAttempts);
                }
                return lastResult;
            }

            if (attempt < maxAttempts) {
                long delayMs = computeDelayMs(policy, attempt);
                log.warn("Action '{}' failed on attempt {}/{}. Retrying in {}ms. Error: {}",
                        actionId, attempt, maxAttempts, delayMs, lastResult.getErrorMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ActionResult.failure("Retry sleep interrupted after attempt " + attempt);
                }
            }
        }

        log.error("Action '{}' exhausted all {} attempts. Last error: {}", actionId, maxAttempts,
                lastResult != null ? lastResult.getErrorMessage() : "unknown");
        return lastResult;
    }

    /**
     * Computes delay in ms for the given attempt number using the configured backoff strategy.
     * attempt is 1-based; on failure of attempt N, we wait before attempt N+1.
     */
    private long computeDelayMs(RetryPolicy policy, int failedAttempt) {
        long initialMs = policy.getInitialDelay().toMillis();
        long maxMs = policy.getMaxDelay().toMillis();

        long delay = switch (policy.getBackoffType()) {
            case FIXED_DELAY -> initialMs;
            case EXPONENTIAL -> (long) (initialMs * Math.pow(2, failedAttempt - 1));
            case JITTER      -> {
                long base = (long) (initialMs * Math.pow(2, failedAttempt - 1));
                long jitter = (long) (Math.random() * initialMs);
                yield base + jitter;
            }
        };

        return Math.min(delay, maxMs);
    }
}
