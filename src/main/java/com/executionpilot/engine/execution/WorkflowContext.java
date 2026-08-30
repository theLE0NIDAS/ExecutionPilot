package com.executionpilot.engine.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkflowContext {

    private final String executionId;
    private final Map<String, Object> data;

    public WorkflowContext(String executionId, Map<String, Object> data) {
        this.executionId = requireText(executionId, "executionId");
        Map<String, Object> safeData = data == null ? Map.of() : data;
        this.data = Map.copyOf(new LinkedHashMap<>(safeData));
    }

    public String getExecutionId() {
        return executionId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public WorkflowContext withValue(String key, Object value) {
        String safeKey = requireText(key, "key");
        Map<String, Object> mutable = new LinkedHashMap<>(data);
        mutable.put(safeKey, value);
        return new WorkflowContext(executionId, mutable);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
