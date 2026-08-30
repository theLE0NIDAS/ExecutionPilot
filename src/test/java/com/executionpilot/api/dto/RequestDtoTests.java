package com.executionpilot.api.dto;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.workflow.domain.StateType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestDtoTests {

    @Test
    void createsWorkflowRequestWithDefensiveCopies() {
        RetryPolicyRequest retryPolicy = new RetryPolicyRequest(3, 100L, 1000L, RetryBackoffType.EXPONENTIAL);
        ActionDefinitionRequest action = new ActionDefinitionRequest(
                "a1",
                "log action",
                ActionType.LOG,
                new HashMap<>(Map.of("message", "hello")),
                retryPolicy
        );
        List<ActionDefinitionRequest> actions = new ArrayList<>(List.of(action));
        StateDefinitionRequest state = new StateDefinitionRequest("start", "Start", StateType.START, actions);
        List<StateDefinitionRequest> states = new ArrayList<>(List.of(state));
        List<TransitionDefinitionRequest> transitions = new ArrayList<>(List.of(
                new TransitionDefinitionRequest("t1", "start", "end", "DONE", "")
        ));

        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "wf-10",
                "Sample",
                1,
                "start",
                "end",
                states,
                transitions
        );

        states.clear();
        transitions.clear();

        assertEquals(1, request.states().size());
        assertEquals(1, request.transitions().size());
        assertThrows(UnsupportedOperationException.class, () -> request.states().add(state));
    }

    @Test
    void validatesRetryPolicyRequestBounds() {
        assertThrows(IllegalArgumentException.class, () ->
                new RetryPolicyRequest(3, -1L, 100L, RetryBackoffType.FIXED_DELAY));
        assertThrows(IllegalArgumentException.class, () ->
                new RetryPolicyRequest(3, 100L, 10L, RetryBackoffType.FIXED_DELAY));
    }
}
