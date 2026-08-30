package com.executionpilot.persistence.mongo.repository;

import com.executionpilot.persistence.mongo.document.ActionExecutionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringActionExecutionDocumentRepository extends MongoRepository<ActionExecutionDocument, String> {

    List<ActionExecutionDocument> findByExecutionIdOrderByStartedAtAsc(String executionId);

    List<ActionExecutionDocument> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId);
}
