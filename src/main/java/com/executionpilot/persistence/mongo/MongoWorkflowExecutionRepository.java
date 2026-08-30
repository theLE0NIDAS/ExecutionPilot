package com.executionpilot.persistence.mongo;

import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.persistence.mongo.document.WorkflowExecutionDocument;
import com.executionpilot.persistence.mongo.mapper.WorkflowExecutionMongoMapper;
import com.executionpilot.persistence.mongo.repository.SpringWorkflowExecutionDocumentRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MongoWorkflowExecutionRepository implements WorkflowExecutionRepository {

    private final SpringWorkflowExecutionDocumentRepository repository;
    private final MongoTemplate mongoTemplate;
    private final WorkflowExecutionMongoMapper mapper = new WorkflowExecutionMongoMapper();

    public MongoWorkflowExecutionRepository(
            SpringWorkflowExecutionDocumentRepository repository,
            MongoTemplate mongoTemplate
    ) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public WorkflowExecution save(WorkflowExecution workflowExecution) {
        WorkflowExecutionDocument saved = repository.save(mapper.toDocument(workflowExecution));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WorkflowExecution> findByExecutionId(String executionId) {
        return repository.findById(executionId).map(mapper::toDomain);
    }

    @Override
    public List<WorkflowExecution> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId) {
        return repository.findByWorkflowIdOrderByUpdatedAtDesc(workflowId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<WorkflowExecution> updateContextIncrementally(
            String executionId,
            Map<String, Object> contextPatch,
            String currentStateId,
            ExecutionStatus status,
            Instant updatedAt
    ) {
        Query query = Query.query(Criteria.where("_id").is(executionId));
        Update update = new Update()
                .set("currentStateId", currentStateId)
                .set("status", status)
                .set("updatedAt", updatedAt);

        if (contextPatch != null) {
            for (Map.Entry<String, Object> entry : contextPatch.entrySet()) {
                update.set("contextData." + entry.getKey(), entry.getValue());
            }
        }

        WorkflowExecutionDocument updated = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WorkflowExecutionDocument.class
        );
        return Optional.ofNullable(updated).map(mapper::toDomain);
    }
}
