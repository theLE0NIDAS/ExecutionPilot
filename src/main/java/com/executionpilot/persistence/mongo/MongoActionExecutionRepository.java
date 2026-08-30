package com.executionpilot.persistence.mongo;

import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.persistence.mongo.document.ActionExecutionDocument;
import com.executionpilot.persistence.mongo.mapper.ActionExecutionMongoMapper;
import com.executionpilot.persistence.mongo.repository.SpringActionExecutionDocumentRepository;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MongoActionExecutionRepository implements ActionExecutionRepository {

    private final SpringActionExecutionDocumentRepository repository;
    private final ActionExecutionMongoMapper mapper = new ActionExecutionMongoMapper();

    public MongoActionExecutionRepository(SpringActionExecutionDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public ActionExecutionRecord save(ActionExecutionRecord actionExecutionRecord) {
        ActionExecutionDocument saved = repository.save(mapper.toDocument(actionExecutionRecord));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ActionExecutionRecord> findByActionExecutionId(String actionExecutionId) {
        return repository.findById(actionExecutionId).map(mapper::toDomain);
    }

    @Override
    public List<ActionExecutionRecord> findByExecutionIdOrderByStartedAtAsc(String executionId) {
        return repository.findByExecutionIdOrderByStartedAtAsc(executionId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ActionExecutionRecord> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId) {
        return repository.findByExecutionIdAndStateIdOrderByAttemptNoAsc(executionId, stateId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
