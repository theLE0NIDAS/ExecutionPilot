package com.executionpilot.common.exception;

import com.executionpilot.workflow.validator.WorkflowValidationResult;

import java.util.Objects;

public class InvalidWorkflowDefinitionException extends RuntimeException {

    private final WorkflowValidationResult validationResult;

    public InvalidWorkflowDefinitionException(String message, WorkflowValidationResult validationResult) {
        super(message);
        this.validationResult = Objects.requireNonNull(validationResult, "validationResult must not be null");
    }

    public WorkflowValidationResult getValidationResult() {
        return validationResult;
    }
}
