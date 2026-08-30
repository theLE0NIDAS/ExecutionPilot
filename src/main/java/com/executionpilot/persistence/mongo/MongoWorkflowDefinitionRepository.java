package com.executionpilot.persistence.mongo;

import com.executionpilot.persistence.mongo.document.WorkflowDocument;
import com.executionpilot.persistence.mongo.mapper.WorkflowMongoMapper;
import com.executionpilot.persistence.mongo.repository.SpringWorkflowDocumentRepository;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MongoWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

    private final SpringWorkflowDocumentRepository repository;
    private final WorkflowMongoMapper mapper = new WorkflowMongoMapper();

    public MongoWorkflowDefinitionRepository(SpringWorkflowDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Workflow save(Workflow workflow) {
        Instant now = Instant.now();
        Optional<WorkflowDocument> existing = repository.findByWorkflowIdAndVersionAndStatus(
                workflow.getWorkflowId(),
                workflow.getVersion(),
                workflow.getStatus()
        );

        WorkflowDocument document = mapper.toDocument(
                workflow,
                existing.map(WorkflowDocument::getCreatedAt).orElse(now),
                now
        );
        existing.map(WorkflowDocument::getId).ifPresent(document::setId);
        WorkflowDocument saved = repository.save(document);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Workflow> findByWorkflowIdAndVersion(String workflowId, int version) {
        return repository.findByWorkflowIdAndVersion(workflowId, version).map(mapper::toDomain);
    }

    @Override
    public Optional<Workflow> findByWorkflowIdAndVersionAndStatus(String workflowId, int version, WorkflowStatus status) {
        return repository.findByWorkflowIdAndVersionAndStatus(workflowId, version, status).map(mapper::toDomain);
    }

    @Override
    public List<Workflow> findByWorkflowIdOrderByVersionDesc(String workflowId) {
        return repository.findByWorkflowIdOrderByVersionDesc(workflowId).stream().map(mapper::toDomain).toList();
    }
}
