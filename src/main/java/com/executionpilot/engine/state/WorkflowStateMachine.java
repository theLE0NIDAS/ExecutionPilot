package com.executionpilot.engine.state;

import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.workflow.domain.Workflow;

import java.time.Instant;
import java.util.Map;

public interface WorkflowStateMachine {

    WorkflowExecution initializeExecution(Workflow workflow, String executionId, Map<String, Object> initialContextData, Instant now);

    StateTransitionDecision completeState(Workflow workflow, WorkflowExecution execution, WorkflowContext updatedContext, Instant now);

    WorkflowExecution failExecution(WorkflowExecution execution, WorkflowContext updatedContext, Instant now);
}
