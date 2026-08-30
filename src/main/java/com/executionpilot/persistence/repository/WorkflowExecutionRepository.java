package com.executionpilot.persistence.repository;

import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowExecution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkflowExecutionRepository {

    WorkflowExecution save(WorkflowExecution workflowExecution);

    Optional<WorkflowExecution> findByExecutionId(String executionId);

    List<WorkflowExecution> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId);

    Optional<WorkflowExecution> updateContextIncrementally(
            String executionId,
            Map<String, Object> contextPatch,
            String currentStateId,
            ExecutionStatus status,
            Instant updatedAt
    );
}
