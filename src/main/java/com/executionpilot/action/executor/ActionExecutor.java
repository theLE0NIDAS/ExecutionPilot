package com.executionpilot.action.executor;

/**
 * Strategy interface for action execution.
 * Each ActionType has one matching implementation.
 * Pattern: Strategy
 */
public interface ActionExecutor {

    ActionResult execute(ActionContext context);

    boolean supports(com.executionpilot.action.definition.ActionType type);
}
