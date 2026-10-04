package com.executionpilot.action.executor.impl;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Placeholder executor for CUSTOM and SCRIPT action types.
 * Returns a no-op success result and logs a warning.
 * Replace with actual scripting engine (e.g., Graal JS, Groovy) in a future phase.
 */
@Component
public class CustomActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(CustomActionExecutor.class);

    @Override
    public ActionResult execute(ActionContext context) {
        log.warn("CustomActionExecutor is a placeholder. Action '{}' of type '{}' was invoked but has no implementation yet.",
                context.getActionDefinition().getActionId(),
                context.getActionDefinition().getType()
        );

        return ActionResult.success(Map.of("note", "custom/script execution not yet implemented"));
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.CUSTOM || type == ActionType.SCRIPT;
    }
}
