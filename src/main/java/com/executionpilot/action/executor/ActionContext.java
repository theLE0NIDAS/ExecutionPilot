package com.executionpilot.action.executor;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.engine.execution.WorkflowContext;

import java.util.Objects;

/**
 * Input contract passed to every ActionExecutor.
 * Carries everything the executor needs: the action definition,
 * the live workflow context, and identifying ids.
 */
public final class ActionContext {

    private final String executionId;
    private final String workflowId;
    private final String stateId;
    private final ActionDefinition actionDefinition;
    private final WorkflowContext workflowContext;

    public ActionContext(
            String executionId,
            String workflowId,
            String stateId,
            ActionDefinition actionDefinition,
            WorkflowContext workflowContext
    ) {
        this.executionId = requireText(executionId, "executionId");
        this.workflowId = requireText(workflowId, "workflowId");
        this.stateId = requireText(stateId, "stateId");
        this.actionDefinition = Objects.requireNonNull(actionDefinition, "actionDefinition must not be null");
        this.workflowContext = Objects.requireNonNull(workflowContext, "workflowContext must not be null");
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getStateId() {
        return stateId;
    }

    public ActionDefinition getActionDefinition() {
        return actionDefinition;
    }

    public WorkflowContext getWorkflowContext() {
        return workflowContext;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
