package com.executionpilot.persistence.mongo.document;

import com.executionpilot.engine.execution.ActionExecutionStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "action_executions")
@CompoundIndexes({
        @CompoundIndex(name = "execution_state_attempt_idx", def = "{'executionId': 1, 'stateId': 1, 'attemptNo': 1}"),
        @CompoundIndex(name = "execution_started_idx", def = "{'executionId': 1, 'startedAt': 1}")
})
public class ActionExecutionDocument {

    @Id
    private String actionExecutionId;

    @Indexed
    private String executionId;

    @Indexed
    private String workflowId;

    @Indexed
    private String stateId;

    @Indexed
    private String actionId;

    private int attemptNo;
    private ActionExecutionStatus status;
    private Object input;
    private Object output;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;

    public String getActionExecutionId() {
        return actionExecutionId;
    }

    public void setActionExecutionId(String actionExecutionId) {
        this.actionExecutionId = actionExecutionId;
    }

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

    public String getStateId() {
        return stateId;
    }

    public void setStateId(String stateId) {
        this.stateId = stateId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public ActionExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ActionExecutionStatus status) {
        this.status = status;
    }

    public Object getInput() {
        return input;
    }

    public void setInput(Object input) {
        this.input = input;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
