package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.domain.Workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorkflowValidatorChain {

    private final List<WorkflowValidator> validators;

    public WorkflowValidatorChain(List<WorkflowValidator> validators) {
        Objects.requireNonNull(validators, "validators must not be null");
        if (validators.isEmpty()) {
            throw new IllegalArgumentException("validators must not be empty");
        }
        this.validators = List.copyOf(new ArrayList<>(validators));
    }

    public static WorkflowValidatorChain defaultChain() {
        return new WorkflowValidatorChain(List.of(
                new StartEndStateValidator(),
                new TransitionIntegrityValidator(),
                new CycleDetectionValidator(),
                new ReachabilityValidator(),
                new KahnTopologicalOrderValidator()
        ));
    }

    public WorkflowValidationResult validate(Workflow workflow) {
        ValidationContext context = ValidationContext.from(workflow);
        WorkflowValidationResult.Builder resultBuilder = WorkflowValidationResult.builder();

        for (WorkflowValidator validator : validators) {
            List<ValidationMessage> messages = validator.validate(workflow, context);
            resultBuilder.addMessages(messages);
            if (messages.stream().anyMatch(ValidationMessage::fatal)) {
                break;
            }
        }

        resultBuilder.topologicalOrder(context.getTopologicalOrder());
        return resultBuilder.build();
    }
}
