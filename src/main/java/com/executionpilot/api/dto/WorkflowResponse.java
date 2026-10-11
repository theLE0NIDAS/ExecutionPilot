package com.executionpilot.api.dto;

import com.executionpilot.workflow.domain.WorkflowStatus;

import java.util.ArrayList;
import java.util.List;

public record WorkflowResponse(
        String workflowId,
        String name,
        int version,
        WorkflowStatus status,
        String startStateId,
        String endStateId,
        List<StateDefinitionResponse> states,
        List<TransitionDefinitionResponse> transitions
) {
    public WorkflowResponse {
        states = states == null ? List.of() : List.copyOf(new ArrayList<>(states));
        transitions = transitions == null ? List.of() : List.copyOf(new ArrayList<>(transitions));
    }
}
