package com.executionpilot.workflow.validator;

import java.util.Objects;

public record ValidationMessage(ValidationErrorCode code, String message, boolean fatal) {

    public ValidationMessage {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
