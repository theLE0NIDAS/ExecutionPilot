package com.executionpilot.workflow.builder;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;

import java.util.ArrayList;
import java.util.List;

public final class StateDefinitionBuilder {

    private String stateId;
    private String name;
    private StateType type;
    private final List<ActionDefinition> actions = new ArrayList<>();

    public StateDefinitionBuilder stateId(String stateId) {
        this.stateId = stateId;
        return this;
    }

    public StateDefinitionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public StateDefinitionBuilder type(StateType type) {
        this.type = type;
        return this;
    }

    public StateDefinitionBuilder addAction(ActionDefinition action) {
        this.actions.add(action);
        return this;
    }

    public StateDefinitionBuilder actions(List<ActionDefinition> actions) {
        this.actions.clear();
        if (actions != null) {
            this.actions.addAll(actions);
        }
        return this;
    }

    public StateDefinition build() {
        return new StateDefinition(stateId, name, type, actions);
    }
}
