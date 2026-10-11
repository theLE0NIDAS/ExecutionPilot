package com.executionpilot.workflow.generator;

public enum WorkflowGenerationMode {
    MANUAL,
    LLM;

    public static WorkflowGenerationMode from(String value) {
        if (value == null || value.isBlank()) {
            return LLM;
        }
        return WorkflowGenerationMode.valueOf(value.trim().toUpperCase());
    }
}
