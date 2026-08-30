package com.executionpilot.persistence.repository;

import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository {

    Workflow save(Workflow workflow);

    Optional<Workflow> findByWorkflowIdAndVersion(String workflowId, int version);

    Optional<Workflow> findByWorkflowIdAndVersionAndStatus(String workflowId, int version, WorkflowStatus status);

    List<Workflow> findByWorkflowIdOrderByVersionDesc(String workflowId);
}
