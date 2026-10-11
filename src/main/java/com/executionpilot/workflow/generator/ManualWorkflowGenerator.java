package com.executionpilot.workflow.generator;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.api.dto.ActionDefinitionRequest;
import com.executionpilot.api.dto.CreateWorkflowRequest;
import com.executionpilot.api.dto.RetryPolicyRequest;
import com.executionpilot.api.dto.StateDefinitionRequest;
import com.executionpilot.api.dto.TransitionDefinitionRequest;
import com.executionpilot.workflow.domain.StateType;

import java.util.List;
import java.util.Map;

public final class ManualWorkflowGenerator implements WorkflowGenerator {

    @Override
    public GeneratedWorkflowDraft generate(String prompt, String workflowName) {
        String safeName = workflowName == null || workflowName.isBlank() ? "Generated Workflow" : workflowName.trim();
        String workflowId = slugify(safeName);

        RetryPolicyRequest retry = new RetryPolicyRequest(1, 0L, 0L, RetryBackoffType.FIXED_DELAY);
        StateDefinitionRequest start = new StateDefinitionRequest(
                "start",
                "Start",
                StateType.START,
                List.of(new ActionDefinitionRequest(
                        "log-start",
                        "Log workflow start",
                        ActionType.LOG,
                        Map.of("message", "Workflow started for: " + (prompt == null ? safeName : prompt)),
                        retry
                ))
        );
        StateDefinitionRequest review = new StateDefinitionRequest(
                "review",
                "Review",
                StateType.NORMAL,
                List.of(new ActionDefinitionRequest(
                        "log-review",
                        "Log workflow review",
                        ActionType.LOG,
                        Map.of("message", "Review step for: " + safeName),
                        retry
                ))
        );
        StateDefinitionRequest end = new StateDefinitionRequest(
                "end",
                "End",
                StateType.END,
                List.of()
        );

        CreateWorkflowRequest request = new CreateWorkflowRequest(
                workflowId,
                safeName,
                1,
                "start",
                "end",
                List.of(start, review, end),
                List.of(
                        new TransitionDefinitionRequest("t1", "start", "review", "CONTINUE", "true"),
                        new TransitionDefinitionRequest("t2", "review", "end", "COMPLETE", "true")
                )
        );

        return new GeneratedWorkflowDraft(request, buildJson(request), WorkflowGenerationMode.MANUAL);
    }

    private String buildJson(CreateWorkflowRequest request) {
        return "{\n"
                + "  \"workflowId\": \"" + request.workflowId() + "\",\n"
                + "  \"name\": \"" + request.name() + "\",\n"
                + "  \"version\": 1,\n"
                + "  \"startStateId\": \"start\",\n"
                + "  \"endStateId\": \"end\",\n"
                + "  \"states\": [\n"
                + "    {\"stateId\": \"start\", \"name\": \"Start\", \"type\": \"START\", \"actions\": [{\"actionId\": \"log-start\", \"name\": \"Log workflow start\", \"type\": \"LOG\", \"config\": {\"message\": \"Workflow started\"}, \"retryPolicy\": {\"maxAttempts\": 1, \"initialDelayMillis\": 0, \"maxDelayMillis\": 0, \"backoffType\": \"FIXED_DELAY\"}}]},\n"
                + "    {\"stateId\": \"review\", \"name\": \"Review\", \"type\": \"NORMAL\", \"actions\": [{\"actionId\": \"log-review\", \"name\": \"Log workflow review\", \"type\": \"LOG\", \"config\": {\"message\": \"Workflow review\"}, \"retryPolicy\": {\"maxAttempts\": 1, \"initialDelayMillis\": 0, \"maxDelayMillis\": 0, \"backoffType\": \"FIXED_DELAY\"}}]},\n"
                + "    {\"stateId\": \"end\", \"name\": \"End\", \"type\": \"END\", \"actions\": []}\n"
                + "  ],\n"
                + "  \"transitions\": [\n"
                + "    {\"transitionId\": \"t1\", \"fromStateId\": \"start\", \"toStateId\": \"review\", \"eventName\": \"CONTINUE\", \"guardExpression\": \"true\"},\n"
                + "    {\"transitionId\": \"t2\", \"fromStateId\": \"review\", \"toStateId\": \"end\", \"eventName\": \"COMPLETE\", \"guardExpression\": \"true\"}\n"
                + "  ]\n"
                + "}";
    }

    private String slugify(String value) {
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String cleaned = normalized.replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "generated-workflow" : cleaned;
    }
}
