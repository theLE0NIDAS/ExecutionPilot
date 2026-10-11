package com.executionpilot.engine.event;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.service.AsyncActionExecutionDispatcher;
import com.executionpilot.engine.state.WorkflowStateMachine;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.Workflow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StateExecutionSignalHandler implements EventHandler<WorkflowAsyncSignal> {

    private final EventBus eventBus;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final AsyncActionExecutionDispatcher asyncActionExecutionDispatcher;
    private final WorkflowStateMachine workflowStateMachine;

    public StateExecutionSignalHandler(
            EventBus eventBus,
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            AsyncActionExecutionDispatcher asyncActionExecutionDispatcher,
            WorkflowStateMachine workflowStateMachine
    ) {
        this.eventBus = eventBus;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.asyncActionExecutionDispatcher = asyncActionExecutionDispatcher;
        this.workflowStateMachine = workflowStateMachine;
        eventBus.subscribe(WorkflowAsyncSignal.class, this);
    }

    @Override
    public String handlerId() {
        return "state-execution-signal-handler";
    }

    @Override
    public void handle(WorkflowAsyncSignal signal) {
        if (signal.getSignalType() != AsyncWorkflowSignalType.STATE_EXECUTION_REQUESTED) {
            return;
        }

        WorkflowExecution execution = workflowExecutionRepository.findByExecutionId(signal.getExecutionId()).orElse(null);
        if (execution == null || execution.getStatus() != ExecutionStatus.WAITING) {
            return;
        }
        if (!execution.getCurrentStateId().equals(signal.getStateId())) {
            return;
        }

        Workflow workflow = workflowDefinitionRepository
                .findByWorkflowIdAndVersion(execution.getWorkflowId(), execution.getWorkflowVersion())
                .orElse(null);
        if (workflow == null) {
            WorkflowExecution failed = failExecution(execution, execution.getContext(),
                    "Workflow definition disappeared during async execution.");
            workflowExecutionRepository.save(failed);
            publishWorkflowFailed(failed, execution.getCurrentStateId(), Map.of("reason", "missing-workflow-definition"));
            return;
        }

        StateDefinition currentState = workflow.getStates().get(execution.getCurrentStateId());
        if (currentState == null) {
            WorkflowExecution failed = failExecution(execution, execution.getContext(),
                    "Current state '" + execution.getCurrentStateId() + "' does not exist in workflow definition.");
            workflowExecutionRepository.save(failed);
            publishWorkflowFailed(failed, execution.getCurrentStateId(), Map.of("reason", "missing-state-definition"));
            return;
        }

        execution = replaceExecution(execution, ExecutionStatus.RUNNING, currentState.getStateId(), execution.getContext(), Instant.now());
        workflowExecutionRepository.save(execution);

        eventBus.publish(WorkflowAsyncSignal.create(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                currentState.getStateId(),
                AsyncWorkflowSignalType.STATE_STARTED,
                "Entered state '" + currentState.getStateId() + "'.",
                Map.of("stateType", currentState.getType().name())
        ));

        WorkflowContext currentContext = execution.getContext();
        int actionIndex = 0;

        for (ActionDefinition actionDefinition : currentState.getActions()) {
            List<ActionExecutionRecord> actionAttempts;
            try {
                actionAttempts = asyncActionExecutionDispatcher.executeActionWithHistoryAsync(
                        execution.getExecutionId(),
                        execution.getWorkflowId(),
                        currentState.getStateId(),
                        actionDefinition,
                        currentContext,
                        1
                ).join();
            } catch (RuntimeException exception) {
                WorkflowContext failedContext = currentContext
                        .withValue("failureStateId", currentState.getStateId())
                        .withValue("failureActionId", actionDefinition.getActionId())
                        .withValue("failureReason", "Async action dispatch failed: " + exception.getMessage());
                WorkflowExecution failed = workflowStateMachine.failExecution(execution, failedContext, Instant.now());
                workflowExecutionRepository.save(failed);
                publishActionFailed(failed, currentState.getStateId(), actionDefinition.getActionId(), 0,
                        "Async action dispatch failed: " + exception.getMessage());
                publishWorkflowFailed(failed, currentState.getStateId(), Map.of(
                        "actionId", actionDefinition.getActionId(),
                        "reason", "async-dispatch-failure"
                ));
                return;
            }

            for (ActionExecutionRecord actionAttempt : actionAttempts) {
                if (actionAttempt.getStatus() == ActionExecutionStatus.RETRYING) {
                    eventBus.publish(WorkflowAsyncSignal.create(
                            execution.getExecutionId(),
                            execution.getWorkflowId(),
                            execution.getWorkflowVersion(),
                            currentState.getStateId(),
                            AsyncWorkflowSignalType.RETRY_SCHEDULED,
                            "Retry scheduled for action '" + actionDefinition.getActionId() + "'.",
                            Map.of(
                                    "actionId", actionDefinition.getActionId(),
                                    "attemptNo", actionAttempt.getAttemptNo(),
                                    "errorMessage", actionAttempt.getErrorMessage()
                            )
                    ));
                }
            }

            ActionExecutionRecord finalActionAttempt = actionAttempts.getLast();
            if (finalActionAttempt.getStatus() != ActionExecutionStatus.SUCCESS) {
                publishActionFailed(
                        execution,
                        currentState.getStateId(),
                        actionDefinition.getActionId(),
                        finalActionAttempt.getAttemptNo(),
                        finalActionAttempt.getErrorMessage()
                );
                WorkflowContext failedContext = currentContext
                        .withValue("failureStateId", currentState.getStateId())
                        .withValue("failureActionId", actionDefinition.getActionId())
                        .withValue("failureReason", finalActionAttempt.getErrorMessage());
                WorkflowExecution failed = workflowStateMachine.failExecution(execution, failedContext, Instant.now());
                workflowExecutionRepository.save(failed);
                publishWorkflowFailed(failed, currentState.getStateId(), Map.of(
                        "actionId", actionDefinition.getActionId(),
                        "attemptNo", finalActionAttempt.getAttemptNo(),
                        "errorMessage", finalActionAttempt.getErrorMessage()
                ));
                return;
            }

            currentContext = mergeActionOutput(currentContext, actionDefinition.getActionId(), finalActionAttempt.getOutput());
            currentContext = currentContext
                    .withValue("lastSuccessfulActionId", actionDefinition.getActionId())
                    .withValue("lastCompletedStateId", currentState.getStateId());
            execution = replaceExecution(execution, ExecutionStatus.RUNNING, currentState.getStateId(), currentContext, Instant.now());
            workflowExecutionRepository.save(execution);

            eventBus.publish(WorkflowAsyncSignal.create(
                    execution.getExecutionId(),
                    execution.getWorkflowId(),
                    execution.getWorkflowVersion(),
                    currentState.getStateId(),
                    AsyncWorkflowSignalType.ACTION_SUCCEEDED,
                    "Action '" + actionDefinition.getActionId() + "' completed successfully.",
                    Map.of(
                            "actionId", actionDefinition.getActionId(),
                            "attemptNo", finalActionAttempt.getAttemptNo(),
                            "actionIndex", actionIndex,
                            "outputPresent", finalActionAttempt.getOutput() != null
                    )
            ));
            actionIndex++;
        }

        eventBus.publish(WorkflowAsyncSignal.create(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                currentState.getStateId(),
                AsyncWorkflowSignalType.STATE_COMPLETED,
                "State '" + currentState.getStateId() + "' completed.",
                Map.of("actionCount", currentState.getActions().size())
        ));
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

    private WorkflowExecution failExecution(WorkflowExecution execution, WorkflowContext currentContext, String failureReason) {
        WorkflowContext failedContext = currentContext.withValue("failureReason", failureReason);
        return workflowStateMachine.failExecution(execution, failedContext, Instant.now());
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

    private void publishActionFailed(
            WorkflowExecution execution,
            String stateId,
            String actionId,
            int attemptNo,
            String errorMessage
    ) {
        eventBus.publish(WorkflowAsyncSignal.create(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                stateId,
                AsyncWorkflowSignalType.ACTION_FAILED,
                "Action '" + actionId + "' failed.",
                Map.of(
                        "actionId", actionId,
                        "attemptNo", attemptNo,
                        "errorMessage", errorMessage
                )
        ));
    }

    private void publishWorkflowFailed(WorkflowExecution execution, String stateId, Map<String, Object> payload) {
        eventBus.publish(WorkflowAsyncSignal.create(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                stateId,
                AsyncWorkflowSignalType.WORKFLOW_FAILED,
                "Workflow execution failed in state '" + stateId + "'.",
                payload
        ));
    }
}
