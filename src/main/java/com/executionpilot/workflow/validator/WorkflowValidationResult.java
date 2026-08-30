package com.executionpilot.workflow.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorkflowValidationResult {

    private final List<ValidationMessage> messages;
    private final List<String> topologicalOrder;

    private WorkflowValidationResult(List<ValidationMessage> messages, List<String> topologicalOrder) {
        this.messages = List.copyOf(new ArrayList<>(messages));
        this.topologicalOrder = List.copyOf(new ArrayList<>(topologicalOrder));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() {
        return messages.isEmpty();
    }

    public boolean hasFatalErrors() {
        return messages.stream().anyMatch(ValidationMessage::fatal);
    }

    public List<ValidationMessage> getMessages() {
        return messages;
    }

    public List<String> getTopologicalOrder() {
        return topologicalOrder;
    }

    public boolean hasErrorCode(ValidationErrorCode code) {
        return messages.stream().anyMatch(message -> message.code() == code);
    }

    public static final class Builder {
        private final List<ValidationMessage> messages = new ArrayList<>();
        private List<String> topologicalOrder = List.of();

        public Builder addMessage(ValidationMessage message) {
            this.messages.add(Objects.requireNonNull(message, "message must not be null"));
            return this;
        }

        public Builder addMessages(List<ValidationMessage> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return this;
        }

        public Builder topologicalOrder(List<String> topologicalOrder) {
            if (topologicalOrder == null) {
                this.topologicalOrder = List.of();
            } else {
                this.topologicalOrder = List.copyOf(topologicalOrder);
            }
            return this;
        }

        public WorkflowValidationResult build() {
            return new WorkflowValidationResult(messages, topologicalOrder);
        }
    }
}
