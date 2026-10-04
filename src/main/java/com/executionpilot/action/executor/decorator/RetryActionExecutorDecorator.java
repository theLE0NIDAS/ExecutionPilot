package com.executionpilot.action.executor.decorator;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.retry.RetryStrategy;
import com.executionpilot.action.retry.RetryStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries the delegate executor according to the action's RetryPolicy.
 * Supports FIXED, LINEAR, and EXPONENTIAL backoff strategies.
 * Pattern: Decorator + Strategy (backoff calculation per RetryBackoffType)
 */
public class RetryActionExecutorDecorator extends ActionExecutorDecorator {

    private static final Logger log = LoggerFactory.getLogger(RetryActionExecutorDecorator.class);
    private final RetryStrategyFactory retryStrategyFactory;

    public RetryActionExecutorDecorator(ActionExecutor delegate) {
        this(delegate, RetryStrategyFactory.defaultFactory());
    }

    public RetryActionExecutorDecorator(ActionExecutor delegate, RetryStrategyFactory retryStrategyFactory) {
        super(delegate);
        this.retryStrategyFactory = retryStrategyFactory;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        RetryPolicy policy = context.getActionDefinition().getRetryPolicy();
        int maxAttempts = policy.getMaxAttempts();
        String actionId = context.getActionDefinition().getActionId();
        RetryStrategy retryStrategy = retryStrategyFactory.getStrategy(policy.getBackoffType());

        ActionResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResult = delegate.execute(context);

            if (lastResult.isSuccess()) {
                if (attempt > 1) {
                    log.info("Action '{}' succeeded on attempt {}/{}", actionId, attempt, maxAttempts);
                }
                return lastResult;
            }

            if (!lastResult.isRetriable()) {
                log.warn("Action '{}' failed with a non-retriable error on attempt {}/{}: {}",
                        actionId, attempt, maxAttempts, lastResult.getErrorMessage());
                return lastResult;
            }

            if (attempt < maxAttempts) {
                long delayMs = retryStrategy.computeDelayMillis(policy, attempt);
                log.warn("Action '{}' failed on attempt {}/{}. Retrying in {}ms. Error: {}",
                        actionId, attempt, maxAttempts, delayMs, lastResult.getErrorMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ActionResult.nonRetriableFailure("Retry sleep interrupted after attempt " + attempt);
                }
            }
        }

        log.error("Action '{}' exhausted all {} attempts. Last error: {}", actionId, maxAttempts,
                lastResult != null ? lastResult.getErrorMessage() : "unknown");
        return lastResult;
    }
}
