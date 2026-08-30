package com.executionpilot.persistence.mongo.mapper;

import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.persistence.mongo.document.EventLogDocument;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EventLogMongoMapper {

    public EventLogDocument toDocument(WorkflowEventLogEntry entry) {
        EventLogDocument document = new EventLogDocument();
        document.setEventId(entry.getEventId());
        document.setExecutionId(entry.getExecutionId());
        document.setWorkflowId(entry.getWorkflowId());
        document.setStateId(entry.getStateId());
        document.setEventType(entry.getEventType());
        document.setMessage(entry.getMessage());
        document.setPayload(new LinkedHashMap<>(entry.getPayload()));
        document.setCreatedAt(entry.getCreatedAt());
        return document;
    }

    public WorkflowEventLogEntry toDomain(EventLogDocument document) {
        Map<String, Object> payload = document.getPayload() == null ? Map.of() : document.getPayload();
        return new WorkflowEventLogEntry(
                document.getEventId(),
                document.getExecutionId(),
                document.getWorkflowId(),
                document.getStateId(),
                document.getEventType(),
                document.getMessage(),
                payload,
                document.getCreatedAt()
        );
    }
}
