package com.executionpilot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TransitionDefinitionRequest(
        @NotBlank String transitionId,
        @NotBlank String fromStateId,
        @NotBlank String toStateId,
        @NotBlank String eventName,
        String guardExpression
) {
}
