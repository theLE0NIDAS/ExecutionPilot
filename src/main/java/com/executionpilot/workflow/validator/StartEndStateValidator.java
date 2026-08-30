package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayList;
import java.util.List;

public final class StartEndStateValidator implements WorkflowValidator {

    @Override
    public List<ValidationMessage> validate(Workflow workflow, ValidationContext context) {
        List<ValidationMessage> messages = new ArrayList<>();

        long startCount = workflow.getStates().values().stream().filter(state -> state.getType() == StateType.START).count();
        long endCount = workflow.getStates().values().stream().filter(state -> state.getType() == StateType.END).count();

        if (startCount == 0) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.MISSING_START_STATE,
                    "Workflow must contain exactly one START state, but none was found.",
                    true
            ));
        } else if (startCount > 1) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.MULTIPLE_START_STATES,
                    "Workflow must contain exactly one START state, but found " + startCount + ".",
                    true
            ));
        }

        if (endCount == 0) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.MISSING_END_STATE,
                    "Workflow must contain exactly one END state, but none was found.",
                    true
            ));
        } else if (endCount > 1) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.MULTIPLE_END_STATES,
                    "Workflow must contain exactly one END state, but found " + endCount + ".",
                    true
            ));
        }

        StateDefinition startState = workflow.getStates().get(workflow.getStartStateId());
        if (startState != null && startState.getType() != StateType.START) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.START_STATE_TYPE_MISMATCH,
                    "startStateId '" + workflow.getStartStateId() + "' is not typed as START.",
                    true
            ));
        }

        StateDefinition endState = workflow.getStates().get(workflow.getEndStateId());
        if (endState != null && endState.getType() != StateType.END) {
            messages.add(new ValidationMessage(
                    ValidationErrorCode.END_STATE_TYPE_MISMATCH,
                    "endStateId '" + workflow.getEndStateId() + "' is not typed as END.",
                    true
            ));
        }

        return messages;
    }
}
