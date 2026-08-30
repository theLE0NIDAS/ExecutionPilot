package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TransitionIntegrityValidator implements WorkflowValidator {

    @Override
    public List<ValidationMessage> validate(Workflow workflow, ValidationContext context) {
        List<ValidationMessage> messages = new ArrayList<>();
        Set<String> transitionSignatures = new HashSet<>();

        for (TransitionDefinition transition : workflow.getTransitions()) {
            if (!workflow.getStates().containsKey(transition.getFromStateId())) {
                messages.add(new ValidationMessage(
                        ValidationErrorCode.INVALID_TRANSITION_SOURCE,
                        "Transition '" + transition.getTransitionId() + "' has invalid source state '" + transition.getFromStateId() + "'.",
                        true
                ));
            }

            if (!workflow.getStates().containsKey(transition.getToStateId())) {
                messages.add(new ValidationMessage(
                        ValidationErrorCode.INVALID_TRANSITION_TARGET,
                        "Transition '" + transition.getTransitionId() + "' has invalid target state '" + transition.getToStateId() + "'.",
                        true
                ));
            }

            if (transition.getFromStateId().equals(transition.getToStateId())) {
                messages.add(new ValidationMessage(
                        ValidationErrorCode.SELF_LOOP_DETECTED,
                        "Transition '" + transition.getTransitionId() + "' creates a self-loop on state '" + transition.getFromStateId() + "'.",
                        true
                ));
            }

            String signature = transition.getFromStateId() + "|" + transition.getToStateId() + "|" + transition.getEventName();
            if (!transitionSignatures.add(signature)) {
                messages.add(new ValidationMessage(
                        ValidationErrorCode.DUPLICATE_TRANSITION,
                        "Duplicate transition detected for edge '" + transition.getFromStateId() + " -> " + transition.getToStateId()
                                + "' with event '" + transition.getEventName() + "'.",
                        true
                ));
            }
        }

        return messages;
    }
}
