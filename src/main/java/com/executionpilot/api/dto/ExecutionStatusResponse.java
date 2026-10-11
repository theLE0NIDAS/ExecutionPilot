package com.executionpilot.api.dto;

import com.executionpilot.engine.execution.ExecutionStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ExecutionStatusResponse(
        String executionId,
        String workflowId,
        int workflowVersion,
        ExecutionStatus status,
        String currentStateId,
        Map<String, Object> context,
        Instant startedAt,
        Instant updatedAt
) {
    public ExecutionStatusResponse {
        context = context == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(context));
    }
}
