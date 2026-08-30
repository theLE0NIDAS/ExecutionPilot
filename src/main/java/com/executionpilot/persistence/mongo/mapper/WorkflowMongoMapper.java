package com.executionpilot.persistence.mongo.mapper;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.persistence.mongo.document.WorkflowDocument;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowMongoMapper {

    public WorkflowDocument toDocument(Workflow workflow, Instant createdAt, Instant updatedAt) {
        WorkflowDocument document = new WorkflowDocument();
        document.setWorkflowId(workflow.getWorkflowId());
        document.setName(workflow.getName());
        document.setVersion(workflow.getVersion());
        document.setStatus(workflow.getStatus());
        document.setStartStateId(workflow.getStartStateId());
        document.setEndStateId(workflow.getEndStateId());
        document.setStates(toStateDocuments(workflow.getStates()));
        document.setTransitions(toTransitionDocuments(workflow.getTransitions()));
        document.setCreatedAt(createdAt);
        document.setUpdatedAt(updatedAt);
        return document;
    }

    public Workflow toDomain(WorkflowDocument document) {
        return new Workflow(
                document.getWorkflowId(),
                document.getName(),
                document.getVersion(),
                document.getStatus(),
                document.getStartStateId(),
                document.getEndStateId(),
                toStateDefinitions(document.getStates()),
                toTransitionDefinitions(document.getTransitions())
        );
    }

    private Map<String, WorkflowDocument.StateDocument> toStateDocuments(Map<String, StateDefinition> states) {
        Map<String, WorkflowDocument.StateDocument> documents = new LinkedHashMap<>();
        for (Map.Entry<String, StateDefinition> entry : states.entrySet()) {
            StateDefinition state = entry.getValue();
            WorkflowDocument.StateDocument stateDocument = new WorkflowDocument.StateDocument();
            stateDocument.setStateId(state.getStateId());
            stateDocument.setName(state.getName());
            stateDocument.setType(state.getType().name());
            stateDocument.setActions(toActionDocuments(state.getActions()));
            documents.put(entry.getKey(), stateDocument);
        }
        return documents;
    }

    private Map<String, StateDefinition> toStateDefinitions(Map<String, WorkflowDocument.StateDocument> documents) {
        Map<String, StateDefinition> states = new LinkedHashMap<>();
        if (documents == null) {
            return states;
        }

        for (Map.Entry<String, WorkflowDocument.StateDocument> entry : documents.entrySet()) {
            WorkflowDocument.StateDocument stateDocument = entry.getValue();
            StateDefinition state = new StateDefinition(
                    stateDocument.getStateId(),
                    stateDocument.getName(),
                    StateType.valueOf(stateDocument.getType()),
                    toActionDefinitions(stateDocument.getActions())
            );
            states.put(entry.getKey(), state);
        }
        return states;
    }

    private List<WorkflowDocument.ActionDocument> toActionDocuments(List<ActionDefinition> actions) {
        List<WorkflowDocument.ActionDocument> documents = new ArrayList<>();
        for (ActionDefinition action : actions) {
            WorkflowDocument.ActionDocument document = new WorkflowDocument.ActionDocument();
            document.setActionId(action.getActionId());
            document.setName(action.getName());
            document.setType(action.getType().name());
            document.setConfig(new LinkedHashMap<>(action.getConfig()));
            document.setRetryPolicy(toRetryPolicyDocument(action.getRetryPolicy()));
            documents.add(document);
        }
        return documents;
    }

    private List<ActionDefinition> toActionDefinitions(List<WorkflowDocument.ActionDocument> documents) {
        if (documents == null) {
            return List.of();
        }

        List<ActionDefinition> actions = new ArrayList<>();
        for (WorkflowDocument.ActionDocument document : documents) {
            actions.add(new ActionDefinition(
                    document.getActionId(),
                    document.getName(),
                    ActionType.valueOf(document.getType()),
                    document.getConfig(),
                    toRetryPolicy(document.getRetryPolicy())
            ));
        }
        return actions;
    }

    private WorkflowDocument.RetryPolicyDocument toRetryPolicyDocument(RetryPolicy policy) {
        WorkflowDocument.RetryPolicyDocument document = new WorkflowDocument.RetryPolicyDocument();
        document.setMaxAttempts(policy.getMaxAttempts());
        document.setInitialDelayMillis(policy.getInitialDelay().toMillis());
        document.setMaxDelayMillis(policy.getMaxDelay().toMillis());
        document.setBackoffType(policy.getBackoffType().name());
        return document;
    }

    private RetryPolicy toRetryPolicy(WorkflowDocument.RetryPolicyDocument document) {
        return new RetryPolicy(
                document.getMaxAttempts(),
                Duration.ofMillis(document.getInitialDelayMillis()),
                Duration.ofMillis(document.getMaxDelayMillis()),
                RetryBackoffType.valueOf(document.getBackoffType())
        );
    }

    private List<WorkflowDocument.TransitionDocument> toTransitionDocuments(List<TransitionDefinition> transitions) {
        List<WorkflowDocument.TransitionDocument> documents = new ArrayList<>();
        for (TransitionDefinition transition : transitions) {
            WorkflowDocument.TransitionDocument document = new WorkflowDocument.TransitionDocument();
            document.setTransitionId(transition.getTransitionId());
            document.setFromStateId(transition.getFromStateId());
            document.setToStateId(transition.getToStateId());
            document.setEventName(transition.getEventName());
            document.setGuardExpression(transition.getGuardExpression());
            documents.add(document);
        }
        return documents;
    }

    private List<TransitionDefinition> toTransitionDefinitions(List<WorkflowDocument.TransitionDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        List<TransitionDefinition> transitions = new ArrayList<>();
        for (WorkflowDocument.TransitionDocument document : documents) {
            transitions.add(new TransitionDefinition(
                    document.getTransitionId(),
                    document.getFromStateId(),
                    document.getToStateId(),
                    document.getEventName(),
                    document.getGuardExpression()
            ));
        }
        return transitions;
    }
}
