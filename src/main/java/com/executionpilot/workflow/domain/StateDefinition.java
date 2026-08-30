package com.executionpilot.workflow.domain;

import com.executionpilot.action.definition.ActionDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StateDefinition {

    private final String stateId;
    private final String name;
    private final StateType type;
    private final List<ActionDefinition> actions;

    public StateDefinition(String stateId, String name, StateType type, List<ActionDefinition> actions) {
        this.stateId = requireText(stateId, "stateId");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
        List<ActionDefinition> safeActions = actions == null ? List.of() : actions;
        this.actions = List.copyOf(new ArrayList<>(safeActions));
    }

    public String getStateId() {
        return stateId;
    }

    public String getName() {
        return name;
    }

    public StateType getType() {
        return type;
    }

    public List<ActionDefinition> getActions() {
        return actions;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
