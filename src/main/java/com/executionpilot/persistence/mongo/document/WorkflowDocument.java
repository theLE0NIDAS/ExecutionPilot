package com.executionpilot.persistence.mongo.document;

import com.executionpilot.workflow.domain.WorkflowStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "workflows")
@CompoundIndexes({
        @CompoundIndex(name = "workflow_version_status_uq", def = "{'workflowId': 1, 'version': 1, 'status': 1}", unique = true),
        @CompoundIndex(name = "workflow_lookup_idx", def = "{'workflowId': 1, 'version': -1}")
})
public class WorkflowDocument {

    @Id
    private String id;

    @Indexed
    private String workflowId;

    private String name;
    private int version;

    @Indexed
    private WorkflowStatus status;

    private String startStateId;
    private String endStateId;

    private Map<String, StateDocument> states;
    private List<TransitionDocument> transitions;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public String getStartStateId() {
        return startStateId;
    }

    public void setStartStateId(String startStateId) {
        this.startStateId = startStateId;
    }

    public String getEndStateId() {
        return endStateId;
    }

    public void setEndStateId(String endStateId) {
        this.endStateId = endStateId;
    }

    public Map<String, StateDocument> getStates() {
        return states;
    }

    public void setStates(Map<String, StateDocument> states) {
        this.states = states;
    }

    public List<TransitionDocument> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionDocument> transitions) {
        this.transitions = transitions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class StateDocument {
        private String stateId;
        private String name;
        private String type;
        private List<ActionDocument> actions;

        public String getStateId() {
            return stateId;
        }

        public void setStateId(String stateId) {
            this.stateId = stateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<ActionDocument> getActions() {
            return actions;
        }

        public void setActions(List<ActionDocument> actions) {
            this.actions = actions;
        }
    }

    public static class TransitionDocument {
        private String transitionId;
        private String fromStateId;
        private String toStateId;
        private String eventName;
        private String guardExpression;

        public String getTransitionId() {
            return transitionId;
        }

        public void setTransitionId(String transitionId) {
            this.transitionId = transitionId;
        }

        public String getFromStateId() {
            return fromStateId;
        }

        public void setFromStateId(String fromStateId) {
            this.fromStateId = fromStateId;
        }

        public String getToStateId() {
            return toStateId;
        }

        public void setToStateId(String toStateId) {
            this.toStateId = toStateId;
        }

        public String getEventName() {
            return eventName;
        }

        public void setEventName(String eventName) {
            this.eventName = eventName;
        }

        public String getGuardExpression() {
            return guardExpression;
        }

        public void setGuardExpression(String guardExpression) {
            this.guardExpression = guardExpression;
        }
    }

    public static class ActionDocument {
        private String actionId;
        private String name;
        private String type;
        private Map<String, Object> config;
        private RetryPolicyDocument retryPolicy;

        public String getActionId() {
            return actionId;
        }

        public void setActionId(String actionId) {
            this.actionId = actionId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }

        public RetryPolicyDocument getRetryPolicy() {
            return retryPolicy;
        }

        public void setRetryPolicy(RetryPolicyDocument retryPolicy) {
            this.retryPolicy = retryPolicy;
        }
    }

    public static class RetryPolicyDocument {
        private int maxAttempts;
        private long initialDelayMillis;
        private long maxDelayMillis;
        private String backoffType;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialDelayMillis() {
            return initialDelayMillis;
        }

        public void setInitialDelayMillis(long initialDelayMillis) {
            this.initialDelayMillis = initialDelayMillis;
        }

        public long getMaxDelayMillis() {
            return maxDelayMillis;
        }

        public void setMaxDelayMillis(long maxDelayMillis) {
            this.maxDelayMillis = maxDelayMillis;
        }

        public String getBackoffType() {
            return backoffType;
        }

        public void setBackoffType(String backoffType) {
            this.backoffType = backoffType;
        }
    }
}
