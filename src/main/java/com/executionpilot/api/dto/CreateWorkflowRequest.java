package com.executionpilot.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record CreateWorkflowRequest(
        @NotBlank String workflowId,
        @NotBlank String name,
        @Min(1) int version,
        @NotBlank String startStateId,
        @NotBlank String endStateId,
        @NotEmpty List<@Valid StateDefinitionRequest> states,
        @NotNull List<@Valid TransitionDefinitionRequest> transitions
) {
    public CreateWorkflowRequest {
        states = List.copyOf(new ArrayList<>(states));
        transitions = List.copyOf(new ArrayList<>(transitions));
    }
}
