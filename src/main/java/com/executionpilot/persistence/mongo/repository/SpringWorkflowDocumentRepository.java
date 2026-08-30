package com.executionpilot.persistence.mongo.repository;

import com.executionpilot.persistence.mongo.document.WorkflowDocument;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpringWorkflowDocumentRepository extends MongoRepository<WorkflowDocument, String> {

    Optional<WorkflowDocument> findByWorkflowIdAndVersion(String workflowId, int version);

    Optional<WorkflowDocument> findByWorkflowIdAndVersionAndStatus(String workflowId, int version, WorkflowStatus status);

    List<WorkflowDocument> findByWorkflowIdOrderByVersionDesc(String workflowId);
}
