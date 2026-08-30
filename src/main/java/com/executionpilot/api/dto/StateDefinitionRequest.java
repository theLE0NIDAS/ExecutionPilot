package com.executionpilot.api.dto;

import com.executionpilot.workflow.domain.StateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record StateDefinitionRequest(
        @NotBlank String stateId,
        @NotBlank String name,
        @NotNull StateType type,
        @NotEmpty List<@Valid ActionDefinitionRequest> actions
) {
    public StateDefinitionRequest {
        actions = List.copyOf(new ArrayList<>(actions));
    }
}
