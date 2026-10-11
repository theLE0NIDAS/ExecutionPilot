package com.executionpilot.api.dto;

import com.executionpilot.action.definition.ActionType;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActionDefinitionResponse(
        String actionId,
        String name,
        ActionType type,
        Map<String, Object> config,
        RetryPolicyResponse retryPolicy
) {
    public ActionDefinitionResponse {
        config = config == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(config));
    }
}
