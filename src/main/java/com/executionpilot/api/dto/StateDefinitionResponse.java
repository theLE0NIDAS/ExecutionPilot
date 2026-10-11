package com.executionpilot.api.dto;

import com.executionpilot.workflow.domain.StateType;

import java.util.ArrayList;
import java.util.List;

public record StateDefinitionResponse(
        String stateId,
        String name,
        StateType type,
        List<ActionDefinitionResponse> actions
) {
    public StateDefinitionResponse {
        actions = actions == null ? List.of() : List.copyOf(new ArrayList<>(actions));
    }
}
