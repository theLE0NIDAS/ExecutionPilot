package com.executionpilot.action.factory;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the appropriate ActionExecutor for a given ActionType.
 * Pattern: Factory Method — the registry is built from all Spring-managed ActionExecutor beans.
 * New executors are registered automatically when the interface is implemented and annotated.
 */
@Component
public class ActionExecutorFactory {

    private final List<ActionExecutor> executors;

    public ActionExecutorFactory(List<ActionExecutor> executors) {
        this.executors = executors;
    }

    /**
     * Returns the first executor that supports the given type.
     * @throws IllegalStateException if no executor is registered for the type
     */
    public ActionExecutor getExecutor(ActionType type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ActionExecutor registered for type: " + type
                ));
    }

    /**
     * Returns true if at least one executor supports the given type.
     */
    public boolean hasExecutor(ActionType type) {
        return executors.stream().anyMatch(e -> e.supports(type));
    }
}
