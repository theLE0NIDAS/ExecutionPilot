package com.executionpilot.workflow.validator;

import com.executionpilot.workflow.builder.StateDefinitionBuilder;
import com.executionpilot.workflow.builder.TransitionDefinitionBuilder;
import com.executionpilot.workflow.builder.WorkflowBuilder;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowValidatorChainTests {

    private final WorkflowValidatorChain chain = WorkflowValidatorChain.defaultChain();

    @Test
    void validatesDagAndProducesStableTopologicalOrder() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-valid")
                .name("valid")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("a", StateType.NORMAL))
                .addState(state("b", StateType.NORMAL))
                .addState(state("end", StateType.END))
                .addTransition(transition("t1", "start", "a", "START_A"))
                .addTransition(transition("t2", "start", "b", "START_B"))
                .addTransition(transition("t3", "a", "end", "A_END"))
                .addTransition(transition("t4", "b", "end", "B_END"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertTrue(result.isValid());
        assertEquals(List.of("start", "a", "b", "end"), result.getTopologicalOrder());
    }

    @Test
    void rejectsMissingStartAndEndTypes() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-no-start-end")
                .name("no-start-end")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("s1")
                .endStateId("s2")
                .addState(state("s1", StateType.NORMAL))
                .addState(state("s2", StateType.NORMAL))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.MISSING_START_STATE));
        assertTrue(result.hasErrorCode(ValidationErrorCode.MISSING_END_STATE));
        assertTrue(result.hasFatalErrors());
    }

    @Test
    void rejectsInvalidTransitionEndpoints() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-invalid-transition")
                .name("invalid-transition")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("end", StateType.END))
                .addTransition(transition("t1", "start", "missing", "X"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.INVALID_TRANSITION_TARGET));
        assertTrue(result.getTopologicalOrder().isEmpty());
    }

    @Test
    void rejectsUnreachableStates() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-unreachable")
                .name("unreachable")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("middle", StateType.NORMAL))
                .addState(state("end", StateType.END))
                .addState(state("orphan", StateType.NORMAL))
                .addTransition(transition("t1", "start", "middle", "STEP"))
                .addTransition(transition("t2", "middle", "end", "DONE"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.UNREACHABLE_STATES));
    }

    @Test
    void rejectsCycles() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-cycle")
                .name("cycle")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("a", StateType.NORMAL))
                .addState(state("end", StateType.END))
                .addTransition(transition("t1", "start", "a", "STEP1"))
                .addTransition(transition("t2", "a", "start", "STEP2"))
                .addTransition(transition("t3", "a", "end", "STEP3"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.CYCLE_DETECTED));
        assertTrue(result.getTopologicalOrder().isEmpty());
    }

    @Test
    void rejectsDuplicateTransitions() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-duplicate")
                .name("duplicate")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("end", StateType.END))
                .addTransition(transition("t1", "start", "end", "DONE"))
                .addTransition(transition("t2", "start", "end", "DONE"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.DUPLICATE_TRANSITION));
    }

    @Test
    void rejectsSelfLoop() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-self-loop")
                .name("self-loop")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("end", StateType.END))
                .addTransition(transition("t1", "start", "start", "LOOP"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.SELF_LOOP_DETECTED));
    }

    @Test
    void stopsValidatorChainOnFatalError() {
        Workflow workflow = new WorkflowBuilder()
                .workflowId("wf-stop-on-fatal")
                .name("stop-on-fatal")
                .version(1)
                .status(WorkflowStatus.DRAFT)
                .startStateId("start")
                .endStateId("end")
                .addState(state("start", StateType.START))
                .addState(state("end", StateType.END))
                .addState(state("orphan", StateType.NORMAL))
                .addTransition(transition("t1", "start", "missing", "BROKEN"))
                .build();

        WorkflowValidationResult result = chain.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.hasErrorCode(ValidationErrorCode.INVALID_TRANSITION_TARGET));
        assertFalse(result.hasErrorCode(ValidationErrorCode.UNREACHABLE_STATES));
    }

    private static StateDefinition state(String stateId, StateType type) {
        return new StateDefinitionBuilder()
                .stateId(stateId)
                .name(stateId)
                .type(type)
                .actions(List.of())
                .build();
    }

    private static TransitionDefinition transition(String transitionId, String from, String to, String event) {
        return new TransitionDefinitionBuilder()
                .transitionId(transitionId)
                .fromStateId(from)
                .toStateId(to)
                .eventName(event)
                .build();
    }
}
