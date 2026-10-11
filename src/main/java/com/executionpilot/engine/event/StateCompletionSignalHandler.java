package com.executionpilot.engine.event;

import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.state.StateTransitionDecision;
import com.executionpilot.engine.state.WorkflowStateMachine;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.domain.Workflow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class StateCompletionSignalHandler implements EventHandler<WorkflowAsyncSignal> {

    private final EventBus eventBus;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowStateMachine workflowStateMachine;

    public StateCompletionSignalHandler(
            EventBus eventBus,
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowStateMachine workflowStateMachine
    ) {
        this.eventBus = eventBus;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowStateMachine = workflowStateMachine;
        eventBus.subscribe(WorkflowAsyncSignal.class, this);
    }

    @Override
    public String handlerId() {
        return "state-completion-signal-handler";
    }

    @Override
    public void handle(WorkflowAsyncSignal signal) {
        if (signal.getSignalType() != AsyncWorkflowSignalType.STATE_COMPLETED) {
            return;
        }

        WorkflowExecution execution = workflowExecutionRepository.findByExecutionId(signal.getExecutionId()).orElse(null);
        if (execution == null || execution.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }
        if (!execution.getCurrentStateId().equals(signal.getStateId())) {
            return;
        }

        Workflow workflow = workflowDefinitionRepository
                .findByWorkflowIdAndVersion(execution.getWorkflowId(), execution.getWorkflowVersion())
                .orElse(null);
        if (workflow == null) {
            WorkflowExecution failed = new WorkflowExecution(
                    execution.getExecutionId(),
                    execution.getWorkflowId(),
                    execution.getWorkflowVersion(),
                    ExecutionStatus.FAILED,
                    execution.getCurrentStateId(),
                    execution.getContext().withValue("failureReason", "Workflow definition disappeared during state completion."),
                    execution.getStartedAt(),
                    Instant.now()
            );
            workflowExecutionRepository.save(failed);
            eventBus.publish(WorkflowAsyncSignal.create(
                    failed.getExecutionId(),
                    failed.getWorkflowId(),
                    failed.getWorkflowVersion(),
                    failed.getCurrentStateId(),
                    AsyncWorkflowSignalType.WORKFLOW_FAILED,
                    "Workflow execution failed in state '" + failed.getCurrentStateId() + "'.",
                    Map.of("reason", "missing-workflow-definition")
            ));
            return;
        }

        StateTransitionDecision decision = workflowStateMachine.completeState(workflow, execution, execution.getContext(), Instant.now());
        ExecutionStatus nextExecutionStatus = decision.getStatus() == ExecutionStatus.RUNNING
                ? ExecutionStatus.WAITING
                : decision.getStatus();
        WorkflowExecution updatedExecution = new WorkflowExecution(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                nextExecutionStatus,
                decision.getNextStateId(),
                execution.getContext(),
                execution.getStartedAt(),
                Instant.now()
        );
        workflowExecutionRepository.save(updatedExecution);

        if (decision.getStatus() == ExecutionStatus.FAILED) {
            eventBus.publish(WorkflowAsyncSignal.create(
                    updatedExecution.getExecutionId(),
                    updatedExecution.getWorkflowId(),
                    updatedExecution.getWorkflowVersion(),
                    signal.getStateId(),
                    AsyncWorkflowSignalType.WORKFLOW_FAILED,
                    decision.getMessage(),
                    Map.of("currentStateId", signal.getStateId())
            ));
            return;
        }

        if (decision.getStatus() == ExecutionStatus.COMPLETED) {
            eventBus.publish(WorkflowAsyncSignal.create(
                    updatedExecution.getExecutionId(),
                    updatedExecution.getWorkflowId(),
                    updatedExecution.getWorkflowVersion(),
                    signal.getStateId(),
                    AsyncWorkflowSignalType.WORKFLOW_COMPLETED,
                    decision.getMessage(),
                    Map.of("currentStateId", signal.getStateId())
            ));
            return;
        }

        eventBus.publish(WorkflowAsyncSignal.create(
                updatedExecution.getExecutionId(),
                updatedExecution.getWorkflowId(),
                updatedExecution.getWorkflowVersion(),
                decision.getNextStateId(),
                AsyncWorkflowSignalType.STATE_EXECUTION_REQUESTED,
                "Scheduled asynchronous state execution for '" + decision.getNextStateId() + "'.",
                Map.of(
                        "transitionId", decision.getTransitionId(),
                        "fromStateId", signal.getStateId()
                )
        ));
    }
}
