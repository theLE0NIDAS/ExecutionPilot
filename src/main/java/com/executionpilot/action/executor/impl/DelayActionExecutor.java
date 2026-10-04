package com.executionpilot.action.executor.impl;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes WAIT actions by sleeping for a configured duration.
 * Config keys: durationMs (required, long)
 */
@Component
public class DelayActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DelayActionExecutor.class);

    @Override
    public ActionResult execute(ActionContext context) {
        var config = context.getActionDefinition().getConfig();
        Object rawMs = config.get("durationMs");

        if (rawMs == null) {
            return ActionResult.failure("WAIT action config is missing required 'durationMs'.");
        }

        long durationMs;
        try {
            durationMs = Long.parseLong(rawMs.toString());
        } catch (NumberFormatException e) {
            return ActionResult.failure("WAIT action 'durationMs' is not a valid number: " + rawMs);
        }

        if (durationMs < 0) {
            return ActionResult.failure("WAIT action 'durationMs' must be non-negative.");
        }

        log.info("Delaying execution for {}ms (executionId={})", durationMs, context.getExecutionId());

        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failure("Delay was interrupted.");
        }

        return ActionResult.success("Waited " + durationMs + "ms");
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.WAIT;
    }
}
