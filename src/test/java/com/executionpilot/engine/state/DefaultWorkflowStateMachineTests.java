package com.executionpilot.engine.state;

import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWorkflowStateMachineTests {

    private final DefaultWorkflowStateMachine stateMachine = new DefaultWorkflowStateMachine();

    @Test
    void initializesExecutionAtStartState() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        Instant now = Instant.now();
        WorkflowExecution execution = stateMachine.initializeExecution(workflow, "exec-1", Map.of("orderId", "ord-1"), now);

        assertThat(execution.getExecutionId()).isEqualTo("exec-1");
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getCurrentStateId()).isEqualTo("start");
        assertThat(execution.getContext().getData()).containsEntry("orderId", "ord-1");
        assertThat(execution.getStartedAt()).isEqualTo(now);
    }

    @Test
    void completesEndStateTerminally() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );
        WorkflowExecution execution = execution("end");

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution, execution.getContext(), Instant.now());

        assertThat(decision.isTerminal()).isTrue();
        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(decision.getNextStateId()).isEqualTo("end");
    }

    @Test
    void advancesWhenExactlyOneOutgoingTransitionExists() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution("start"), new WorkflowContext("exec-1", Map.of()), Instant.now());

        assertThat(decision.isTerminal()).isFalse();
        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(decision.getTransitionId()).isEqualTo("t1");
        assertThat(decision.getNextStateId()).isEqualTo("end");
    }

    @Test
    void failsWhenCurrentStateIsMissingFromWorkflowDefinition() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution("ghost"), new WorkflowContext("exec-1", Map.of()), Instant.now());

        assertThat(decision.isTerminal()).isTrue();
        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(decision.getMessage()).contains("does not exist");
    }

    @Test
    void failsWhenNonTerminalStateHasNoOutgoingTransition() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of()
        );

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution("start"), new WorkflowContext("exec-1", Map.of()), Instant.now());

        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(decision.getMessage()).contains("No outgoing transition");
    }

    @Test
    void failsWhenStateHasMultipleOutgoingTransitions() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "a", new StateDefinition("a", "A", StateType.NORMAL, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(
                        new TransitionDefinition("t1", "start", "a", "STEP_A", ""),
                        new TransitionDefinition("t2", "start", "end", "STEP_END", "")
                )
        );

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution("start"), new WorkflowContext("exec-1", Map.of()), Instant.now());

        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(decision.getMessage()).contains("Multiple outgoing transitions");
    }

    @Test
    void failsWhenTransitionPointsToMissingState() {
        Workflow workflow = workflow(
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "missing", "DONE", ""))
        );

        StateTransitionDecision decision = stateMachine.completeState(workflow, execution("start"), new WorkflowContext("exec-1", Map.of()), Instant.now());

        assertThat(decision.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(decision.getMessage()).contains("points to missing state");
    }

    @Test
    void failExecutionCreatesFailedSnapshot() {
        WorkflowExecution running = execution("start");

        WorkflowExecution failed = stateMachine.failExecution(running, new WorkflowContext("exec-1", Map.of("failureReason", "boom")), Instant.now());

        assertThat(failed.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failed.getCurrentStateId()).isEqualTo("start");
        assertThat(failed.getContext().getData()).containsEntry("failureReason", "boom");
    }

    private static Workflow workflow(Map<String, StateDefinition> states, List<TransitionDefinition> transitions) {
        return new Workflow("wf-1", "Workflow", 1, WorkflowStatus.ACTIVE, "start", "end", states, transitions);
    }

    private static WorkflowExecution execution(String stateId) {
        Instant now = Instant.now();
        return new WorkflowExecution(
                "exec-1",
                "wf-1",
                1,
                ExecutionStatus.RUNNING,
                stateId,
                new WorkflowContext("exec-1", Map.of()),
                now,
                now
        );
    }
}
