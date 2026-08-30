package com.executionpilot.persistence.mongo.mapper;

import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.persistence.mongo.document.ActionExecutionDocument;

public final class ActionExecutionMongoMapper {

    public ActionExecutionDocument toDocument(ActionExecutionRecord record) {
        ActionExecutionDocument document = new ActionExecutionDocument();
        document.setActionExecutionId(record.getActionExecutionId());
        document.setExecutionId(record.getExecutionId());
        document.setWorkflowId(record.getWorkflowId());
        document.setStateId(record.getStateId());
        document.setActionId(record.getActionId());
        document.setAttemptNo(record.getAttemptNo());
        document.setStatus(record.getStatus());
        document.setInput(record.getInput());
        document.setOutput(record.getOutput());
        document.setErrorMessage(record.getErrorMessage());
        document.setStartedAt(record.getStartedAt());
        document.setFinishedAt(record.getFinishedAt());
        return document;
    }

    public ActionExecutionRecord toDomain(ActionExecutionDocument document) {
        return new ActionExecutionRecord(
                document.getActionExecutionId(),
                document.getExecutionId(),
                document.getWorkflowId(),
                document.getStateId(),
                document.getActionId(),
                document.getAttemptNo(),
                document.getStatus(),
                document.getInput(),
                document.getOutput(),
                document.getErrorMessage(),
                document.getStartedAt(),
                document.getFinishedAt()
        );
    }
}
