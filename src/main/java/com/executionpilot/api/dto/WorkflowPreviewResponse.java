package com.executionpilot.api.dto;

import java.util.ArrayList;
import java.util.List;

public record WorkflowPreviewResponse(
        boolean valid,
        String summary,
        List<String> validationErrors,
        List<String> topologicalOrder
) {
    public WorkflowPreviewResponse {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(new ArrayList<>(validationErrors));
        topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(new ArrayList<>(topologicalOrder));
    }
}
