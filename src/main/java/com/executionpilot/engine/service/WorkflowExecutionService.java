package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.engine.event.WorkflowEventType;
import com.executionpilot.engine.event.WorkflowRuntimeEvent;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.state.StateTransitionDecision;
import com.executionpilot.engine.state.WorkflowStateMachine;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowEventLogRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowExecutionService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowEventLogRepository workflowEventLogRepository;
    private final ActionExecutionService actionExecutionService;
    private final WorkflowStateMachine workflowStateMachine;

    public WorkflowExecutionService(
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowEventLogRepository workflowEventLogRepository,
            ActionExecutionService actionExecutionService,
            WorkflowStateMachine workflowStateMachine
    ) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowEventLogRepository = workflowEventLogRepository;
        this.actionExecutionService = actionExecutionService;
        this.workflowStateMachine = workflowStateMachine;
    }

    public WorkflowExecution executeWorkflow(String workflowId, int workflowVersion, Map<String, Object> initialContextData) {
        Workflow workflow = workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndStatus(workflowId, workflowVersion, WorkflowStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active workflow definition not found for workflowId='" + workflowId
                                + "' and version=" + workflowVersion
                ));

        Instant now = Instant.now();
        String executionId = UUID.randomUUID().toString();
        WorkflowExecution execution = workflowStateMachine.initializeExecution(workflow, executionId, initialContextData, now);
        execution = workflowExecutionRepository.save(execution);

        while (true) {
            StateDefinition currentState = workflow.getStates().get(execution.getCurrentStateId());
            persistEvent(new WorkflowRuntimeEvent(
                    execution.getExecutionId(),
                    workflow.getWorkflowId(),
                    currentState.getStateId(),
                    WorkflowEventType.STATE_STARTED,
                    "Entered state '" + currentState.getStateId() + "'.",
                    Map.of("stateType", currentState.getType().name()),
                    Instant.now()
            ));

            WorkflowContext currentContext = execution.getContext();
            int actionIndex = 0;

            for (ActionDefinition actionDefinition : currentState.getActions()) {
                List<ActionExecutionRecord> actionAttempts = actionExecutionService.executeActionWithHistory(
                        execution.getExecutionId(),
                        workflow.getWorkflowId(),
                        currentState.getStateId(),
                        actionDefinition,
                        currentContext,
                        1
                );

                for (ActionExecutionRecord actionAttempt : actionAttempts) {
                    if (actionAttempt.getStatus() == ActionExecutionStatus.RETRYING) {
                        persistEvent(new WorkflowRuntimeEvent(
                                execution.getExecutionId(),
                                workflow.getWorkflowId(),
                                currentState.getStateId(),
                                WorkflowEventType.RETRY_SCHEDULED,
                                "Retry scheduled for action '" + actionDefinition.getActionId() + "'.",
                                Map.of(
                                        "actionId", actionDefinition.getActionId(),
                                        "attemptNo", actionAttempt.getAttemptNo(),
                                        "errorMessage", actionAttempt.getErrorMessage()
                                ),
                                actionAttempt.getFinishedAt()
                        ));
                    }
                }

                ActionExecutionRecord finalActionAttempt = actionAttempts.getLast();
                if (finalActionAttempt.getStatus() != ActionExecutionStatus.SUCCESS) {
                    persistEvent(new WorkflowRuntimeEvent(
                            execution.getExecutionId(),
                            workflow.getWorkflowId(),
                            currentState.getStateId(),
                            WorkflowEventType.ACTION_FAILED,
                            "Action '" + actionDefinition.getActionId() + "' failed.",
                            Map.of(
                                    "actionId", actionDefinition.getActionId(),
                                    "attemptNo", finalActionAttempt.getAttemptNo(),
                                    "errorMessage", finalActionAttempt.getErrorMessage()
                            ),
                            finalActionAttempt.getFinishedAt()
                    ));

                    WorkflowContext failedContext = currentContext
                            .withValue("failureStateId", currentState.getStateId())
                            .withValue("failureActionId", actionDefinition.getActionId())
                            .withValue("failureReason", finalActionAttempt.getErrorMessage());
                    execution = workflowStateMachine.failExecution(execution, failedContext, Instant.now());
                    execution = workflowExecutionRepository.save(execution);

                    persistEvent(new WorkflowRuntimeEvent(
                            execution.getExecutionId(),
                            workflow.getWorkflowId(),
                            currentState.getStateId(),
                            WorkflowEventType.WORKFLOW_FAILED,
                            "Workflow execution failed in state '" + currentState.getStateId() + "'.",
                            Map.of(
                                    "actionId", actionDefinition.getActionId(),
                                    "attemptNo", finalActionAttempt.getAttemptNo(),
                                    "errorMessage", finalActionAttempt.getErrorMessage()
                            ),
                            Instant.now()
                    ));
                    return execution;
                }

                currentContext = mergeActionOutput(currentContext, actionDefinition.getActionId(), finalActionAttempt.getOutput());
                currentContext = currentContext
                        .withValue("lastSuccessfulActionId", actionDefinition.getActionId())
                        .withValue("lastCompletedStateId", currentState.getStateId());
                execution = replaceExecution(execution, ExecutionStatus.RUNNING, currentState.getStateId(), currentContext, Instant.now());
                execution = workflowExecutionRepository.save(execution);

                persistEvent(new WorkflowRuntimeEvent(
                        execution.getExecutionId(),
                        workflow.getWorkflowId(),
                        currentState.getStateId(),
                        WorkflowEventType.ACTION_SUCCEEDED,
                        "Action '" + actionDefinition.getActionId() + "' completed successfully.",
                        Map.of(
                                "actionId", actionDefinition.getActionId(),
                                "attemptNo", finalActionAttempt.getAttemptNo(),
                                "actionIndex", actionIndex,
                                "outputPresent", finalActionAttempt.getOutput() != null
                        ),
                        finalActionAttempt.getFinishedAt()
                ));
                actionIndex++;
            }

            persistEvent(new WorkflowRuntimeEvent(
                    execution.getExecutionId(),
                    workflow.getWorkflowId(),
                    currentState.getStateId(),
                    WorkflowEventType.STATE_COMPLETED,
                    "State '" + currentState.getStateId() + "' completed.",
                    Map.of("actionCount", currentState.getActions().size()),
                    Instant.now()
            ));

            StateTransitionDecision transitionDecision = workflowStateMachine.completeState(
                    workflow,
                    execution,
                    currentContext,
                    Instant.now()
            );

            execution = replaceExecution(
                    execution,
                    transitionDecision.getStatus(),
                    transitionDecision.getNextStateId(),
                    currentContext,
                    Instant.now()
            );
            execution = workflowExecutionRepository.save(execution);

            if (transitionDecision.getStatus() == ExecutionStatus.FAILED) {
                persistEvent(new WorkflowRuntimeEvent(
                        execution.getExecutionId(),
                        workflow.getWorkflowId(),
                        currentState.getStateId(),
                        WorkflowEventType.WORKFLOW_FAILED,
                        transitionDecision.getMessage(),
                        Map.of("currentStateId", currentState.getStateId()),
                        Instant.now()
                ));
                return execution;
            }

            if (transitionDecision.getStatus() == ExecutionStatus.COMPLETED) {
                persistEvent(new WorkflowRuntimeEvent(
                        execution.getExecutionId(),
                        workflow.getWorkflowId(),
                        currentState.getStateId(),
                        WorkflowEventType.WORKFLOW_COMPLETED,
                        transitionDecision.getMessage(),
                        Map.of("currentStateId", currentState.getStateId()),
                        Instant.now()
                ));
                return execution;
            }
        }
    }

    private WorkflowContext mergeActionOutput(WorkflowContext currentContext, String actionId, Object actionOutput) {
        if (actionOutput == null) {
            return currentContext;
        }

        Object existingActionOutputs = currentContext.getData().get("actionOutputs");
        Map<String, Object> mergedActionOutputs = new LinkedHashMap<>();
        if (existingActionOutputs instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    mergedActionOutputs.put(key, entry.getValue());
                }
            }
        }
        mergedActionOutputs.put(actionId, actionOutput);
        return currentContext.withValue("actionOutputs", Map.copyOf(mergedActionOutputs));
    }

    private WorkflowExecution replaceExecution(
            WorkflowExecution existingExecution,
            ExecutionStatus status,
            String currentStateId,
            WorkflowContext context,
            Instant updatedAt
    ) {
        return new WorkflowExecution(
                existingExecution.getExecutionId(),
                existingExecution.getWorkflowId(),
                existingExecution.getWorkflowVersion(),
                status,
                currentStateId,
                context,
                existingExecution.getStartedAt(),
                updatedAt
        );
    }

    private WorkflowEventLogEntry persistEvent(WorkflowRuntimeEvent runtimeEvent) {
        return workflowEventLogRepository.save(new WorkflowEventLogEntry(
                UUID.randomUUID().toString(),
                runtimeEvent.getExecutionId(),
                runtimeEvent.getWorkflowId(),
                runtimeEvent.getStateId(),
                runtimeEvent.getEventType().name(),
                runtimeEvent.getMessage(),
                runtimeEvent.getPayload(),
                runtimeEvent.getOccurredAt()
        ));
    }
}
