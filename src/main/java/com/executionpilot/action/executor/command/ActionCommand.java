package com.executionpilot.action.executor.command;

import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;

/**
 * Wraps a single action execution as a self-contained executable unit.
 * Pattern: Command — decouples the caller from executor invocation.
 * The command can be queued, scheduled, or retried without knowing the caller.
 */
public final class ActionCommand {

    private final ActionContext context;
    private final ActionExecutor executor;

    public ActionCommand(ActionContext context, ActionExecutor executor) {
        this.context = context;
        this.executor = executor;
    }

    /**
     * Executes the action. May be called multiple times for retries.
     */
    public ActionResult execute() {
        return executor.execute(context);
    }

    public ActionContext getContext() {
        return context;
    }

    public ActionExecutor getExecutor() {
        return executor;
    }
}
