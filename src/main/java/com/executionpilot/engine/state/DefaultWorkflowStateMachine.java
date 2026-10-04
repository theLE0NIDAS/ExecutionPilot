package com.executionpilot.engine.state;

import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DefaultWorkflowStateMachine implements WorkflowStateMachine {

    @Override
    public WorkflowExecution initializeExecution(
            Workflow workflow,
            String executionId,
            Map<String, Object> initialContextData,
            Instant now
    ) {
        WorkflowContext context = new WorkflowContext(executionId, initialContextData);
        return new WorkflowExecution(
                executionId,
                workflow.getWorkflowId(),
                workflow.getVersion(),
                ExecutionStatus.RUNNING,
                workflow.getStartStateId(),
                context,
                now,
                now
        );
    }

    @Override
    public StateTransitionDecision completeState(
            Workflow workflow,
            WorkflowExecution execution,
            WorkflowContext updatedContext,
            Instant now
    ) {
        StateDefinition currentState = workflow.getStates().get(execution.getCurrentStateId());
        if (currentState == null) {
            return StateTransitionDecision.fail(execution.getCurrentStateId(),
                    "Current state '" + execution.getCurrentStateId() + "' does not exist in workflow definition.");
        }

        if (currentState.getType() == StateType.END) {
            return StateTransitionDecision.complete(currentState.getStateId(),
                    "Terminal end state '" + currentState.getStateId() + "' completed.");
        }

        List<TransitionDefinition> outgoingTransitions = workflow.getTransitions().stream()
                .filter(transition -> transition.getFromStateId().equals(currentState.getStateId()))
                .toList();

        if (outgoingTransitions.isEmpty()) {
            return StateTransitionDecision.fail(currentState.getStateId(),
                    "No outgoing transition found for non-terminal state '" + currentState.getStateId() + "'.");
        }

        if (outgoingTransitions.size() > 1) {
            return StateTransitionDecision.fail(currentState.getStateId(),
                    "Multiple outgoing transitions found for state '" + currentState.getStateId()
                            + "'. Guard-based branching is not implemented yet.");
        }

        TransitionDefinition transition = outgoingTransitions.getFirst();
        if (!workflow.getStates().containsKey(transition.getToStateId())) {
            return StateTransitionDecision.fail(currentState.getStateId(),
                    "Transition '" + transition.getTransitionId() + "' points to missing state '" + transition.getToStateId() + "'.");
        }

        return StateTransitionDecision.advance(
                transition.getToStateId(),
                transition.getTransitionId(),
                "Advanced from state '" + currentState.getStateId() + "' to '" + transition.getToStateId() + "'."
        );
    }

    @Override
    public WorkflowExecution failExecution(WorkflowExecution execution, WorkflowContext updatedContext, Instant now) {
        return new WorkflowExecution(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                ExecutionStatus.FAILED,
                execution.getCurrentStateId(),
                updatedContext,
                execution.getStartedAt(),
                now
        );
    }
}
