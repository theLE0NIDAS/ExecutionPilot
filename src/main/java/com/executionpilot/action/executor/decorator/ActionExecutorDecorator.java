package com.executionpilot.action.executor.decorator;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;

import java.util.Objects;

/**
 * Abstract base for decorator-chain wrappers around ActionExecutor.
 * Pattern: Decorator — extends behavior without subclassing concrete executors.
 */
public abstract class ActionExecutorDecorator implements ActionExecutor {

    protected final ActionExecutor delegate;

    protected ActionExecutorDecorator(ActionExecutor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate executor must not be null");
    }

    @Override
    public boolean supports(ActionType type) {
        return delegate.supports(type);
    }

    @Override
    public abstract ActionResult execute(ActionContext context);
}
