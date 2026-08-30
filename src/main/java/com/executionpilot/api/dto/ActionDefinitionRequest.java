package com.executionpilot.api.dto;

import com.executionpilot.action.definition.ActionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActionDefinitionRequest(
        @NotBlank String actionId,
        @NotBlank String name,
        @NotNull ActionType type,
        @NotNull Map<String, Object> config,
        @Valid @NotNull RetryPolicyRequest retryPolicy
) {
    public ActionDefinitionRequest {
        config = Map.copyOf(new LinkedHashMap<>(config));
    }
}
