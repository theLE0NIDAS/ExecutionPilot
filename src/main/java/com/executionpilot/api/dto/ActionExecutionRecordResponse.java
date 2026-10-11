package com.executionpilot.api.dto;

import com.executionpilot.engine.execution.ActionExecutionStatus;

import java.time.Instant;

public record ActionExecutionRecordResponse(
        String actionExecutionId,
        String executionId,
        String workflowId,
        String stateId,
        String actionId,
        int attemptNo,
        ActionExecutionStatus status,
        Object input,
        Object output,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
