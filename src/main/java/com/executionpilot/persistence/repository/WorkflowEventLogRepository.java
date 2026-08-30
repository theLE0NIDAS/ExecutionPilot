package com.executionpilot.persistence.repository;

import com.executionpilot.engine.event.WorkflowEventLogEntry;

import java.util.List;
import java.util.Optional;

public interface WorkflowEventLogRepository {

    WorkflowEventLogEntry save(WorkflowEventLogEntry eventLogEntry);

    Optional<WorkflowEventLogEntry> findByEventId(String eventId);

    List<WorkflowEventLogEntry> findByExecutionIdOrderByCreatedAtAsc(String executionId);
}
