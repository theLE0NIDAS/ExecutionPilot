package com.executionpilot.action.definition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ActionDefinition {

    private final String actionId;
    private final String name;
    private final ActionType type;
    private final Map<String, Object> config;
    private final RetryPolicy retryPolicy;

    public ActionDefinition(
            String actionId,
            String name,
            ActionType type,
            Map<String, Object> config,
            RetryPolicy retryPolicy
    ) {
        this.actionId = requireText(actionId, "actionId");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type must not be null");
        Map<String, Object> safeConfig = config == null ? Map.of() : config;
        this.config = Map.copyOf(new LinkedHashMap<>(safeConfig));
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }

    public String getActionId() {
        return actionId;
    }

    public String getName() {
        return name;
    }

    public ActionType getType() {
        return type;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
