package com.executionpilot.workflow.generator;

public interface WorkflowGenerator {
    GeneratedWorkflowDraft generate(String prompt, String workflowName);
}
