package com.executionpilot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateWorkflowRequest(
        @NotBlank String prompt,
        @NotBlank String workflowName
) {
}
