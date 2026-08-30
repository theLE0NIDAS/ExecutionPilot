package com.executionpilot.persistence.repository;

import com.executionpilot.engine.execution.ActionExecutionRecord;

import java.util.List;
import java.util.Optional;

public interface ActionExecutionRepository {

    ActionExecutionRecord save(ActionExecutionRecord actionExecutionRecord);

    Optional<ActionExecutionRecord> findByActionExecutionId(String actionExecutionId);

    List<ActionExecutionRecord> findByExecutionIdOrderByStartedAtAsc(String executionId);

    List<ActionExecutionRecord> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId);
}
