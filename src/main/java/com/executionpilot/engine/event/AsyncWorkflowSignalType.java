package com.executionpilot.engine.event;

public enum AsyncWorkflowSignalType {
    STATE_EXECUTION_REQUESTED,
    STATE_STARTED,
    STATE_COMPLETED,
    ACTION_SUCCEEDED,
    ACTION_FAILED,
    RETRY_SCHEDULED,
    WORKFLOW_COMPLETED,
    WORKFLOW_FAILED
}
