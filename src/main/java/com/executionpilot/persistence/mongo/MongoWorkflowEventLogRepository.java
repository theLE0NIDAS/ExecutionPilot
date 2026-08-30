package com.executionpilot.persistence.mongo;

import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.persistence.mongo.document.EventLogDocument;
import com.executionpilot.persistence.mongo.mapper.EventLogMongoMapper;
import com.executionpilot.persistence.mongo.repository.SpringEventLogDocumentRepository;
import com.executionpilot.persistence.repository.WorkflowEventLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MongoWorkflowEventLogRepository implements WorkflowEventLogRepository {

    private final SpringEventLogDocumentRepository repository;
    private final EventLogMongoMapper mapper = new EventLogMongoMapper();

    public MongoWorkflowEventLogRepository(SpringEventLogDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkflowEventLogEntry save(WorkflowEventLogEntry eventLogEntry) {
        EventLogDocument saved = repository.save(mapper.toDocument(eventLogEntry));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WorkflowEventLogEntry> findByEventId(String eventId) {
        return repository.findById(eventId).map(mapper::toDomain);
    }

    @Override
    public List<WorkflowEventLogEntry> findByExecutionIdOrderByCreatedAtAsc(String executionId) {
        return repository.findByExecutionIdOrderByCreatedAtAsc(executionId).stream().map(mapper::toDomain).toList();
    }
}
