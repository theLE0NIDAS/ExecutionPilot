package com.executionpilot.workflow.builder;

import com.executionpilot.workflow.domain.TransitionDefinition;

public final class TransitionDefinitionBuilder {

    private String transitionId;
    private String fromStateId;
    private String toStateId;
    private String eventName;
    private String guardExpression = "";

    public TransitionDefinitionBuilder transitionId(String transitionId) {
        this.transitionId = transitionId;
        return this;
    }

    public TransitionDefinitionBuilder fromStateId(String fromStateId) {
        this.fromStateId = fromStateId;
        return this;
    }

    public TransitionDefinitionBuilder toStateId(String toStateId) {
        this.toStateId = toStateId;
        return this;
    }

    public TransitionDefinitionBuilder eventName(String eventName) {
        this.eventName = eventName;
        return this;
    }

    public TransitionDefinitionBuilder guardExpression(String guardExpression) {
        this.guardExpression = guardExpression;
        return this;
    }

    public TransitionDefinition build() {
        return new TransitionDefinition(transitionId, fromStateId, toStateId, eventName, guardExpression);
    }
}
