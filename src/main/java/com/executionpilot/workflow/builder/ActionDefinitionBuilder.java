package com.executionpilot.workflow.builder;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ActionDefinitionBuilder {

    private String actionId;
    private String name;
    private ActionType type;
    private final Map<String, Object> config = new LinkedHashMap<>();
    private RetryPolicy retryPolicy;

    public ActionDefinitionBuilder actionId(String actionId) {
        this.actionId = actionId;
        return this;
    }

    public ActionDefinitionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ActionDefinitionBuilder type(ActionType type) {
        this.type = type;
        return this;
    }

    public ActionDefinitionBuilder putConfig(String key, Object value) {
        this.config.put(key, value);
        return this;
    }

    public ActionDefinitionBuilder config(Map<String, Object> config) {
        this.config.clear();
        if (config != null) {
            this.config.putAll(config);
        }
        return this;
    }

    public ActionDefinitionBuilder retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    public ActionDefinition build() {
        return new ActionDefinition(actionId, name, type, config, retryPolicy);
    }
}
