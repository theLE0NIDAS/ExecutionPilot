package com.executionpilot.api.dto;

public record TransitionDefinitionResponse(
        String transitionId,
        String fromStateId,
        String toStateId,
        String eventName,
        String guardExpression
) {
}
