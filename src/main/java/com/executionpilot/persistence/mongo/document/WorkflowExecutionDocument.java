package com.executionpilot.persistence.mongo.document;

import com.executionpilot.engine.execution.ExecutionStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "workflow_executions")
@CompoundIndexes({
        @CompoundIndex(name = "workflow_execution_lookup_idx", def = "{'workflowId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "workflow_version_lookup_idx", def = "{'workflowId': 1, 'workflowVersion': 1}")
})
public class WorkflowExecutionDocument {

    @Id
    private String executionId;

    @Indexed
    private String workflowId;

    private int workflowVersion;

    @Indexed
    private ExecutionStatus status;

    @Indexed
    private String currentStateId;

    private Map<String, Object> contextData;

    private Instant startedAt;
    private Instant updatedAt;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getCurrentStateId() {
        return currentStateId;
    }

    public void setCurrentStateId(String currentStateId) {
        this.currentStateId = currentStateId;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public void setContextData(Map<String, Object> contextData) {
        this.contextData = contextData;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
