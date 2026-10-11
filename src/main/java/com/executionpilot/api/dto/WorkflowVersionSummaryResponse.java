package com.executionpilot.api.dto;

import com.executionpilot.workflow.domain.WorkflowStatus;

public record WorkflowVersionSummaryResponse(
        String workflowId,
        String name,
        int version,
        WorkflowStatus status
) {
}
