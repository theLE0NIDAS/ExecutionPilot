package com.executionpilot.action.executor.decorator;

import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs action entry, exit, duration, and result status around delegate execution.
 * Pattern: Decorator
 */
public class LoggingActionExecutorDecorator extends ActionExecutorDecorator {

    private static final Logger log = LoggerFactory.getLogger(LoggingActionExecutorDecorator.class);

    public LoggingActionExecutorDecorator(ActionExecutor delegate) {
        super(delegate);
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String actionId = context.getActionDefinition().getActionId();
        String executionId = context.getExecutionId();

        log.info("Starting action '{}' for executionId={}", actionId, executionId);
        long start = System.currentTimeMillis();

        ActionResult result = delegate.execute(context);

        long durationMs = System.currentTimeMillis() - start;

        if (result.isSuccess()) {
            log.info("Action '{}' completed successfully in {}ms (executionId={})", actionId, durationMs, executionId);
        } else {
            log.warn("Action '{}' failed in {}ms (executionId={}): {}", actionId, durationMs, executionId, result.getErrorMessage());
        }

        return result;
    }
}
