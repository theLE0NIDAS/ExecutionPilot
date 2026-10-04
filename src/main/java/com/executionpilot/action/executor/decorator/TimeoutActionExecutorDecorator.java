package com.executionpilot.action.executor.decorator;

import com.executionpilot.action.executor.ActionExecutionException;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enforces a wall-clock timeout on the delegate executor.
 * Config key: timeoutMs (long, optional — falls back to 30000ms default if not set).
 * Pattern: Decorator
 */
public class TimeoutActionExecutorDecorator extends ActionExecutorDecorator {

    private static final Logger log = LoggerFactory.getLogger(TimeoutActionExecutorDecorator.class);
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    public TimeoutActionExecutorDecorator(ActionExecutor delegate) {
        super(delegate);
    }

    @Override
    public ActionResult execute(ActionContext context) {
        var config = context.getActionDefinition().getConfig();
        long timeoutMs = DEFAULT_TIMEOUT_MS;

        if (config.containsKey("timeoutMs")) {
            try {
                timeoutMs = Long.parseLong(config.get("timeoutMs").toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid timeoutMs config for action '{}'; using default {}ms",
                        context.getActionDefinition().getActionId(), DEFAULT_TIMEOUT_MS);
            }
        }

        if (timeoutMs < 1) {
            log.warn("Non-positive timeoutMs config for action '{}'; using default {}ms",
                    context.getActionDefinition().getActionId(), DEFAULT_TIMEOUT_MS);
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        final long finalTimeoutMs = timeoutMs;
        final ActionContext finalContext = context;

        CompletableFuture<ActionResult> future = CompletableFuture.supplyAsync(
                () -> delegate.execute(finalContext)
        );

        try {
            return future.get(finalTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            String msg = "Action '%s' timed out after %dms".formatted(
                    context.getActionDefinition().getActionId(), finalTimeoutMs);
            log.error(msg);
            return ActionResult.retryableFailure(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.nonRetriableFailure("Action execution was interrupted.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Action '{}' threw exception: {}", context.getActionDefinition().getActionId(), cause.getMessage());
            if (cause instanceof ActionExecutionException actionExecutionException) {
                return actionExecutionException.isRetriable()
                        ? ActionResult.retryableFailure("Action threw exception: " + cause.getMessage())
                        : ActionResult.nonRetriableFailure("Action threw exception: " + cause.getMessage());
            }
            return ActionResult.nonRetriableFailure("Action threw exception: " + cause.getMessage());
        }
    }
}
