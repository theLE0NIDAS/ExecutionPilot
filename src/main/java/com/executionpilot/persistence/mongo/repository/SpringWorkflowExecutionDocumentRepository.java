package com.executionpilot.persistence.mongo.repository;

import com.executionpilot.persistence.mongo.document.WorkflowExecutionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SpringWorkflowExecutionDocumentRepository extends MongoRepository<WorkflowExecutionDocument, String> {

    List<WorkflowExecutionDocument> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId);
}
