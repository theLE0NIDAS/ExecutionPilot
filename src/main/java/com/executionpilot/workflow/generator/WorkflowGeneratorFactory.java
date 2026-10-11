package com.executionpilot.workflow.generator;

public final class WorkflowGeneratorFactory {

    private WorkflowGeneratorFactory() {
    }

    public static WorkflowGenerator get(WorkflowGenerationMode mode) {
        return switch (mode) {
            case MANUAL -> new ManualWorkflowGenerator();
            case LLM -> new LlmWorkflowGenerator();
        };
    }
}
