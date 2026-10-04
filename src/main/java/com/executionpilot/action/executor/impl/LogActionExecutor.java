package com.executionpilot.action.executor.impl;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes LOG actions by writing a message to the application log.
 * Config keys: message (required), level (default INFO)
 */
@Component
public class LogActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(LogActionExecutor.class);

    @Override
    public ActionResult execute(ActionContext context) {
        var config = context.getActionDefinition().getConfig();
        String message = config.getOrDefault("message", "(no message configured)").toString();
        String level = config.getOrDefault("level", "INFO").toString().toUpperCase();

        String formatted = "[WorkflowExecution: %s | State: %s | Action: %s] %s"
                .formatted(
                        context.getExecutionId(),
                        context.getStateId(),
                        context.getActionDefinition().getActionId(),
                        message
                );

        switch (level) {
            case "DEBUG" -> log.debug(formatted);
            case "WARN"  -> log.warn(formatted);
            case "ERROR" -> log.error(formatted);
            default      -> log.info(formatted);
        }

        return ActionResult.success(message);
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.LOG;
    }
}
