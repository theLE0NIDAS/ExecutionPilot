package com.executionpilot.workflow.domain;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.workflow.builder.ActionDefinitionBuilder;
import com.executionpilot.workflow.builder.RetryPolicyBuilder;
import com.executionpilot.workflow.builder.StateDefinitionBuilder;
import com.executionpilot.workflow.builder.TransitionDefinitionBuilder;
import com.executionpilot.workflow.builder.WorkflowBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowDomainModelTests {

    @Test
    void buildsWorkflowUsingBuilders() {
        ActionDefinition logAction = new ActionDefinitionBuilder()
                .actionId("action-log")
                .name("log state transition")
                .type(ActionType.LOG)
                .putConfig("message", "hello")
                .retryPolicy(new RetryPolicyBuilder()
                        .maxAttempts(3)
                        .initialDelay(Duration.ofMillis(50))
                        .maxDelay(Duration.ofSeconds(1))
                        .backoffType(RetryBackoffType.EXPONENTIAL)
                        .build())
                .build();

        StateDefinition start = new StateDefinitionBuilder()
                .stateId("start")
                .name("Start")
                .type(StateType.START)
                .addAction(logAction)
                .build();

        StateDefinition end = new StateDefinitionBuilder()
                .stateId("end")
                .name("End")
                .type(StateType.END)
                .actions(List.of())
                .build();

        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-onboarding")
                .name("Onboarding")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(start)
                .addState(end)
                .addTransition(new TransitionDefinitionBuilder()
                        .transitionId("t1")
                        .fromStateId("start")
                        .toStateId("end")
                        .eventName("START_DONE")
                        .guardExpression("")
                        .build())
                .build();

        assertEquals("wf-onboarding", workflow.getWorkflowId());
        assertEquals(2, workflow.getStates().size());
        assertEquals(1, workflow.getTransitions().size());
        assertEquals(ActionType.LOG, workflow.getStates().get("start").getActions().getFirst().getType());
    }

    @Test
    void enforcesImmutabilityAndInvariants() {
        StateDefinition start = new StateDefinitionBuilder()
                .stateId("start")
                .name("Start")
                .type(StateType.START)
                .actions(List.of())
                .build();
        StateDefinition end = new StateDefinitionBuilder()
                .stateId("end")
                .name("End")
                .type(StateType.END)
                .actions(List.of())
                .build();

        Map<String, StateDefinition> states = new LinkedHashMap<>();
        states.put("start", start);
        states.put("end", end);

        List<TransitionDefinition> transitions = new ArrayList<>();
        transitions.add(new TransitionDefinitionBuilder()
                .transitionId("t1")
                .fromStateId("start")
                .toStateId("end")
                .eventName("DONE")
                .build());

        Workflow workflow = new Workflow(
                "wf-1", "Flow", 1, WorkflowStatus.ACTIVE, "start", "end", states, transitions
        );

        states.put("extra", end);
        transitions.clear();
        assertEquals(2, workflow.getStates().size());
        assertEquals(1, workflow.getTransitions().size());
        assertThrows(UnsupportedOperationException.class, () -> workflow.getTransitions().add(null));

        assertThrows(IllegalArgumentException.class, () ->
                new Workflow("", "Flow", 1, WorkflowStatus.DRAFT, "start", "end", Map.of("start", start, "end", end), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new Workflow("wf-2", "Flow", 1, WorkflowStatus.DRAFT, "missing", "end", Map.of("start", start, "end", end), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new com.executionpilot.action.definition.RetryPolicy(0, Duration.ZERO, Duration.ZERO, RetryBackoffType.FIXED_DELAY));
    }

    @Test
    void modelsWorkflowExecutionAndActionExecutionRecord() {
        WorkflowContext context = new WorkflowContext("exec-1", Map.of("orderId", "ord-11"));
        WorkflowContext updatedContext = context.withValue("step", "start");
        assertFalse(updatedContext.getData().isEmpty());

        Instant now = Instant.now();
        WorkflowExecution execution = new WorkflowExecution(
                "exec-1",
                "wf-1",
                1,
                ExecutionStatus.RUNNING,
                "start",
                updatedContext,
                now,
                now
        );

        ActionExecutionRecord record = new ActionExecutionRecord(
                "act-exec-1",
                execution.getExecutionId(),
                execution.getWorkflowId(),
                "start",
                "action-1",
                1,
                ActionExecutionStatus.SUCCESS,
                Map.of("a", "b"),
                Map.of("ok", true),
                "",
                now,
                now
        );

        assertEquals("exec-1", execution.getExecutionId());
        assertEquals("act-exec-1", record.getActionExecutionId());
        assertThrows(IllegalArgumentException.class, () ->
                new ActionExecutionRecord("x", "e", "w", "s", "a", 1,
                        ActionExecutionStatus.SUCCESS, null, null, "", now, now.minusSeconds(1)));
    }
}
