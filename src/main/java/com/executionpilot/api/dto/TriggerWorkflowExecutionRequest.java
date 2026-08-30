package com.executionpilot.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public record TriggerWorkflowExecutionRequest(
        @NotBlank String workflowId,
        @Min(1) int workflowVersion,
        @NotNull Map<String, Object> context
) {
    public TriggerWorkflowExecutionRequest {
        context = Map.copyOf(new LinkedHashMap<>(context));
    }
}
