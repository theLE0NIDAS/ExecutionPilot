package com.executionpilot.workflow.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Workflow {

    private final String workflowId;
    private final String name;
    private final int version;
    private final WorkflowStatus status;
    private final String startStateId;
    private final String endStateId;
    private final Map<String, StateDefinition> states;
    private final List<TransitionDefinition> transitions;

    public Workflow(
            String workflowId,
            String name,
            int version,
            WorkflowStatus status,
            String startStateId,
            String endStateId,
            Map<String, StateDefinition> states,
            List<TransitionDefinition> transitions
    ) {
        this.workflowId = requireText(workflowId, "workflowId");
        this.name = requireText(name, "name");
        this.version = requirePositive(version, "version");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startStateId = requireText(startStateId, "startStateId");
        this.endStateId = requireText(endStateId, "endStateId");

        Map<String, StateDefinition> safeStates = states == null ? Map.of() : states;
        if (safeStates.isEmpty()) {
            throw new IllegalArgumentException("states must not be empty");
        }
        this.states = Map.copyOf(new LinkedHashMap<>(safeStates));

        List<TransitionDefinition> safeTransitions = transitions == null ? List.of() : transitions;
        this.transitions = List.copyOf(new ArrayList<>(safeTransitions));

        if (!this.states.containsKey(this.startStateId)) {
            throw new IllegalArgumentException("startStateId must exist in states");
        }
        if (!this.states.containsKey(this.endStateId)) {
            throw new IllegalArgumentException("endStateId must exist in states");
        }
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public String getStartStateId() {
        return startStateId;
    }

    public String getEndStateId() {
        return endStateId;
    }

    public Map<String, StateDefinition> getStates() {
        return states;
    }

    public List<TransitionDefinition> getTransitions() {
        return transitions;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return value;
    }
}
