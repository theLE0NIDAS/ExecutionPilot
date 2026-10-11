package com.executionpilot.workflow.generator;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.api.dto.ActionDefinitionRequest;
import com.executionpilot.api.dto.CreateWorkflowRequest;
import com.executionpilot.api.dto.RetryPolicyRequest;
import com.executionpilot.api.dto.StateDefinitionRequest;
import com.executionpilot.api.dto.TransitionDefinitionRequest;
import com.executionpilot.workflow.domain.StateType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LlmWorkflowGenerator implements WorkflowGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GeneratedWorkflowDraft generate(String prompt, String workflowName) {
        String safeName = workflowName == null || workflowName.isBlank() ? "LLM Workflow" : workflowName.trim();
        String workflowId = slugify(safeName);
        String rawJson = buildPromptResponse(prompt, workflowId, safeName);
        CreateWorkflowRequest request = parseGeneratedWorkflow(rawJson);
        return new GeneratedWorkflowDraft(request, rawJson, WorkflowGenerationMode.LLM);
    }

    public CreateWorkflowRequest parseGeneratedWorkflow(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("LLM workflow payload must not be blank.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.has("workflowId") || !root.has("name") || !root.has("version")
                    || !root.has("startStateId") || !root.has("endStateId")
                    || !root.has("states") || !root.has("transitions")) {
                throw new IllegalArgumentException("LLM workflow payload is missing required fields.");
            }

            JsonNode statesNode = root.get("states");
            JsonNode transitionsNode = root.get("transitions");
            if (!statesNode.isArray() || !transitionsNode.isArray()) {
                throw new IllegalArgumentException("LLM workflow states and transitions must be arrays.");
            }

            List<StateDefinitionRequest> states = new ArrayList<>();
            for (JsonNode stateNode : statesNode) {
                String stateId = requireText(stateNode, "stateId");
                String stateName = requireText(stateNode, "name");
                StateType stateType = StateType.valueOf(requireText(stateNode, "type"));
                List<ActionDefinitionRequest> actions = new ArrayList<>();
                JsonNode actionNodes = stateNode.get("actions");
                if (actionNodes != null && actionNodes.isArray()) {
                    for (JsonNode actionNode : actionNodes) {
                        actions.add(parseAction(actionNode));
                    }
                }
                states.add(new StateDefinitionRequest(stateId, stateName, stateType, actions));
            }

            List<TransitionDefinitionRequest> transitions = new ArrayList<>();
            for (JsonNode transitionNode : transitionsNode) {
                transitions.add(new TransitionDefinitionRequest(
                        requireText(transitionNode, "transitionId"),
                        requireText(transitionNode, "fromStateId"),
                        requireText(transitionNode, "toStateId"),
                        requireText(transitionNode, "eventName"),
                        optionalText(transitionNode, "guardExpression")
                ));
            }

            return new CreateWorkflowRequest(
                    requireText(root, "workflowId"),
                    requireText(root, "name"),
                    root.get("version").asInt(1),
                    requireText(root, "startStateId"),
                    requireText(root, "endStateId"),
                    states,
                    transitions
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("LLM workflow payload could not be parsed into a valid workflow definition.", ex);
        }
    }

    private String buildPromptResponse(String prompt, String workflowId, String workflowName) {
        String promptText = prompt == null || prompt.isBlank() ? "Create a basic review workflow" : prompt.trim();
        String stateJson = """
        {
          "workflowId": "%s",
          "name": "%s",
          "version": 1,
          "startStateId": "start",
          "endStateId": "end",
          "states": [
            {
              "stateId": "start",
              "name": "Start",
              "type": "START",
              "actions": [{
                "actionId": "log-start",
                "name": "Log workflow start",
                "type": "LOG",
                "config": {"message": "%s"},
                "retryPolicy": {
                  "maxAttempts": 1,
                  "initialDelayMillis": 0,
                  "maxDelayMillis": 0,
                  "backoffType": "FIXED_DELAY"
                }
              }]
            },
            {
              "stateId": "review",
              "name": "Review",
              "type": "NORMAL",
              "actions": [{
                "actionId": "log-review",
                "name": "Log review step",
                "type": "LOG",
                "config": {"message": "Review workflow step"},
                "retryPolicy": {
                  "maxAttempts": 2,
                  "initialDelayMillis": 100,
                  "maxDelayMillis": 500,
                  "backoffType": "EXPONENTIAL"
                }
              }]
            },
            {
              "stateId": "end",
              "name": "End",
              "type": "END",
              "actions": []
            }
          ],
          "transitions": [
            {"transitionId": "t1", "fromStateId": "start", "toStateId": "review", "eventName": "CONTINUE", "guardExpression": "true"},
            {"transitionId": "t2", "fromStateId": "review", "toStateId": "end", "eventName": "COMPLETE", "guardExpression": "true"}
          ]
        }
        """.formatted(workflowId, workflowName, promptText.replace("\"", "\\\""));
        return stateJson;
    }

    private ActionDefinitionRequest parseAction(JsonNode actionNode) {
        JsonNode retryNode = actionNode.get("retryPolicy");
        RetryPolicyRequest retryPolicy = new RetryPolicyRequest(
                retryNode != null && retryNode.has("maxAttempts") ? retryNode.get("maxAttempts").asInt(1) : 1,
                retryNode != null && retryNode.has("initialDelayMillis") ? retryNode.get("initialDelayMillis").asLong(0L) : 0L,
                retryNode != null && retryNode.has("maxDelayMillis") ? retryNode.get("maxDelayMillis").asLong(0L) : 0L,
                retryNode != null && retryNode.has("backoffType")
                        ? RetryBackoffType.valueOf(retryNode.get("backoffType").asText())
                        : RetryBackoffType.FIXED_DELAY
        );

        Map<String, Object> config = objectMapper.convertValue(
                actionNode.has("config") ? actionNode.get("config") : Map.of(),
                Map.class
        );

        return new ActionDefinitionRequest(
                requireText(actionNode, "actionId"),
                requireText(actionNode, "name"),
                ActionType.valueOf(requireText(actionNode, "type")),
                config,
                retryPolicy
        );
    }

    private String requireText(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull() || node.get(fieldName).asText().isBlank()) {
            throw new IllegalArgumentException("LLM workflow field '" + fieldName + "' is required.");
        }
        return node.get(fieldName).asText();
    }

    private String optionalText(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName) == null || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private String slugify(String value) {
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String cleaned = normalized.replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "llm-generated-workflow" : cleaned;
    }
}
