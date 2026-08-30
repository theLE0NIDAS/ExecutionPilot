package com.executionpilot.persistence.mongo.mapper;

import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.persistence.mongo.document.WorkflowExecutionDocument;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkflowExecutionMongoMapper {

    public WorkflowExecutionDocument toDocument(WorkflowExecution execution) {
        WorkflowExecutionDocument document = new WorkflowExecutionDocument();
        document.setExecutionId(execution.getExecutionId());
        document.setWorkflowId(execution.getWorkflowId());
        document.setWorkflowVersion(execution.getWorkflowVersion());
        document.setStatus(execution.getStatus());
        document.setCurrentStateId(execution.getCurrentStateId());
        document.setContextData(new LinkedHashMap<>(execution.getContext().getData()));
        document.setStartedAt(execution.getStartedAt());
        document.setUpdatedAt(execution.getUpdatedAt());
        return document;
    }

    public WorkflowExecution toDomain(WorkflowExecutionDocument document) {
        Map<String, Object> contextData = document.getContextData() == null ? Map.of() : document.getContextData();
        return new WorkflowExecution(
                document.getExecutionId(),
                document.getWorkflowId(),
                document.getWorkflowVersion(),
                document.getStatus(),
                document.getCurrentStateId(),
                new WorkflowContext(document.getExecutionId(), contextData),
                document.getStartedAt(),
                document.getUpdatedAt()
        );
    }
}
