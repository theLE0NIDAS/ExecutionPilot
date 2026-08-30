package com.executionpilot.persistence.mongo.repository;

import com.executionpilot.persistence.mongo.document.EventLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringEventLogDocumentRepository extends MongoRepository<EventLogDocument, String> {

    List<EventLogDocument> findByExecutionIdOrderByCreatedAtAsc(String executionId);
}
