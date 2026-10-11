package com.executionpilot.workflow.generator;

import com.executionpilot.api.dto.CreateWorkflowRequest;

public record GeneratedWorkflowDraft(
        CreateWorkflowRequest request,
        String rawJson,
        WorkflowGenerationMode mode
) {
}
