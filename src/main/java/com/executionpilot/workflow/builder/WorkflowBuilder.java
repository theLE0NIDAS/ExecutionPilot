package com.executionpilot.workflow.builder;

import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowBuilder {

    private String workflowId;
    private String name;
    private int version = 1;
    private WorkflowStatus status = WorkflowStatus.DRAFT;
    private String startStateId;
    private String endStateId;
    private final Map<String, StateDefinition> states = new LinkedHashMap<>();
    private final List<TransitionDefinition> transitions = new ArrayList<>();

    public WorkflowBuilder workflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public WorkflowBuilder name(String name) {
        this.name = name;
        return this;
    }

    public WorkflowBuilder version(int version) {
        this.version = version;
        return this;
    }

    public WorkflowBuilder status(WorkflowStatus status) {
        this.status = status;
        return this;
    }

    public WorkflowBuilder startStateId(String startStateId) {
        this.startStateId = startStateId;
        return this;
    }

    public WorkflowBuilder endStateId(String endStateId) {
        this.endStateId = endStateId;
        return this;
    }

    public WorkflowBuilder addState(StateDefinition state) {
        if (state != null) {
            this.states.put(state.getStateId(), state);
        }
        return this;
    }

    public WorkflowBuilder states(Map<String, StateDefinition> states) {
        this.states.clear();
        if (states != null) {
            this.states.putAll(states);
        }
        return this;
    }

    public WorkflowBuilder addTransition(TransitionDefinition transition) {
        if (transition != null) {
            this.transitions.add(transition);
        }
        return this;
    }

    public WorkflowBuilder transitions(List<TransitionDefinition> transitions) {
        this.transitions.clear();
        if (transitions != null) {
            this.transitions.addAll(transitions);
        }
        return this;
    }

    public Workflow build() {
        return new Workflow(workflowId, name, version, status, startStateId, endStateId, states, transitions);
    }
}
