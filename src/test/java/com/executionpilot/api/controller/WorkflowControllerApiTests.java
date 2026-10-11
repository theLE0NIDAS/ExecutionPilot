package com.executionpilot.api.controller;

import com.executionpilot.action.executor.impl.LogActionExecutor;
import com.executionpilot.api.dto.ActionDefinitionRequest;
import com.executionpilot.api.dto.CreateWorkflowRequest;
import com.executionpilot.api.dto.GenerateWorkflowRequest;
import com.executionpilot.api.dto.RetryPolicyRequest;
import com.executionpilot.api.dto.StateDefinitionRequest;
import com.executionpilot.api.dto.TransitionDefinitionRequest;
import com.executionpilot.api.dto.TriggerWorkflowExecutionRequest;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowEventLogRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowControllerApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createActivateTriggerAndQueryWorkflowLifecycle() throws Exception {
        CreateWorkflowRequest request = validWorkflowRequest("wf-api", 1);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowId").value("wf-api"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/workflows/wf-api/versions/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-api"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/workflows/wf-api/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-api"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.states.length()").value(2));

        mockMvc.perform(get("/api/workflows/wf-api/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workflowId").value("wf-api"))
                .andExpect(jsonPath("$[0].version").value(1));

        String triggerResponse = mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new TriggerWorkflowExecutionRequest("wf-api", 1, Map.of("orderId", "ord-1")))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowId").value("wf-api"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode triggerNode = objectMapper.readTree(triggerResponse);
        String executionId = triggerNode.get("executionId").asText();

        JsonNode terminalStatus = waitForTerminalExecution(executionId);
        assertThat(terminalStatus.get("status").asText()).isEqualTo(ExecutionStatus.COMPLETED.name());

        mockMvc.perform(get("/api/executions/{executionId}/actions", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].executionId").value(executionId))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void updateDraftAllowsReplacingDraftDefinition() throws Exception {
        CreateWorkflowRequest request = validWorkflowRequest("wf-update", 1);
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());

        CreateWorkflowRequest updated = new CreateWorkflowRequest(
                "wf-update",
                "Updated Workflow Name",
                1,
                "start",
                "end",
                request.states(),
                request.transitions()
        );

        mockMvc.perform(put("/api/workflows/wf-update/versions/1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Workflow Name"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void activateReturnsValidationErrorsForInvalidDraft() throws Exception {
        CreateWorkflowRequest invalid = invalidWorkflowRequestWithOrphanState("wf-invalid", 1);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workflows/wf-invalid/versions/1/activate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKFLOW"))
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void previewWorkflowReturnsValidationFeedback() throws Exception {
        CreateWorkflowRequest invalid = invalidWorkflowRequestWithOrphanState("wf-preview", 1);

        mockMvc.perform(post("/api/workflows/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.validationErrors[0]").exists());
    }

    @Test
    void generateWorkflowDraftFromPromptCreatesDraftOnly() throws Exception {
        GenerateWorkflowRequest request = new GenerateWorkflowRequest(
                "Create a workflow for processing customer orders and shipping approval",
                "Order Processing",
                "LLM"
        );

        mockMvc.perform(post("/api/workflows/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowId").value("order-processing"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void exportAndImportWorkflowRoundTrip() throws Exception {
        CreateWorkflowRequest request = validWorkflowRequest("wf-export", 1);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());

        String exportBody = mockMvc.perform(get("/api/workflows/wf-export/versions/1/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode exportNode = objectMapper.readTree(exportBody);
        assertThat(exportNode.get("workflowId").asText()).isEqualTo("wf-export");

        JsonNode imported = exportNode.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) imported).put("workflowId", "wf-imported");
        ((com.fasterxml.jackson.databind.node.ObjectNode) imported).put("name", "Imported Workflow");
        ((com.fasterxml.jackson.databind.node.ObjectNode) imported).put("version", 1);

        mockMvc.perform(post("/api/workflows/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imported)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workflowId").value("wf-imported"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void validatesRequestBodyAndMissingResources() throws Exception {
        String invalidPayload = """
                {
                  "workflowId": "",
                  "name": "Bad",
                  "version": 1,
                  "startStateId": "start",
                  "endStateId": "end",
                  "states": [],
                  "transitions": []
                }
                """;

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/executions/missing-execution"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsPathBodyMismatchOnDraftUpdate() throws Exception {
        CreateWorkflowRequest request = validWorkflowRequest("wf-mismatch", 1);
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());

        CreateWorkflowRequest mismatched = new CreateWorkflowRequest(
                "wf-other",
                request.name(),
                1,
                request.startStateId(),
                request.endStateId(),
                request.states(),
                request.transitions()
        );

        mockMvc.perform(put("/api/workflows/wf-mismatch/versions/1/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(mismatched)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private JsonNode waitForTerminalExecution(String executionId) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            String body = mockMvc.perform(get("/api/executions/{executionId}", executionId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode node = objectMapper.readTree(body);
            String status = node.get("status").asText();
            if (ExecutionStatus.COMPLETED.name().equals(status) || ExecutionStatus.FAILED.name().equals(status)) {
                return node;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Execution did not reach terminal status within timeout.");
    }

    private CreateWorkflowRequest validWorkflowRequest(String workflowId, int version) {
        RetryPolicyRequest retryPolicy = new RetryPolicyRequest(1, 0L, 0L, RetryBackoffType.FIXED_DELAY);
        ActionDefinitionRequest startAction = new ActionDefinitionRequest(
                "log-start",
                "Log Start",
                ActionType.LOG,
                Map.of("message", "hello"),
                retryPolicy
        );
        StateDefinitionRequest start = new StateDefinitionRequest(
                "start",
                "Start",
                StateType.START,
                List.of(startAction)
        );
        StateDefinitionRequest end = new StateDefinitionRequest(
                "end",
                "End",
                StateType.END,
                List.of()
        );
        TransitionDefinitionRequest transition = new TransitionDefinitionRequest(
                "t1",
                "start",
                "end",
                "DONE",
                ""
        );
        return new CreateWorkflowRequest(
                workflowId,
                "Workflow " + workflowId,
                version,
                "start",
                "end",
                List.of(start, end),
                List.of(transition)
        );
    }

    private CreateWorkflowRequest invalidWorkflowRequestWithOrphanState(String workflowId, int version) {
        CreateWorkflowRequest valid = validWorkflowRequest(workflowId, version);
        List<StateDefinitionRequest> states = new ArrayList<>(valid.states());
        states.add(new StateDefinitionRequest("orphan", "Orphan", StateType.NORMAL, List.of()));
        return new CreateWorkflowRequest(
                valid.workflowId(),
                valid.name(),
                valid.version(),
                valid.startStateId(),
                valid.endStateId(),
                states,
                valid.transitions()
        );
    }

    @TestConfiguration
    static class ApiTestConfiguration {

        @Bean
        @Primary
        WorkflowDefinitionRepository workflowDefinitionRepository() {
            return new InMemoryWorkflowDefinitionRepository();
        }

        @Bean
        @Primary
        WorkflowExecutionRepository workflowExecutionRepository() {
            return new InMemoryWorkflowExecutionRepository();
        }

        @Bean
        @Primary
        WorkflowEventLogRepository workflowEventLogRepository() {
            return new InMemoryWorkflowEventLogRepository();
        }

        @Bean
        @Primary
        ActionExecutionRepository actionExecutionRepository() {
            return new InMemoryActionExecutionRepository();
        }

        @Bean
        @Primary
        LogActionExecutor logActionExecutor() {
            return new LogActionExecutor();
        }
    }

    static final class InMemoryWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

        private final Map<String, Workflow> store = new LinkedHashMap<>();

        @Override
        public synchronized Workflow save(Workflow workflow) {
            store.put(key(workflow.getWorkflowId(), workflow.getVersion(), workflow.getStatus()), workflow);
            return workflow;
        }

        @Override
        public synchronized Optional<Workflow> findByWorkflowIdAndVersion(String workflowId, int version) {
            return store.values().stream()
                    .filter(workflow -> workflow.getWorkflowId().equals(workflowId) && workflow.getVersion() == version)
                    .findFirst();
        }

        @Override
        public synchronized Optional<Workflow> findByWorkflowIdAndVersionAndStatus(String workflowId, int version, WorkflowStatus status) {
            return Optional.ofNullable(store.get(key(workflowId, version, status)));
        }

        @Override
        public synchronized List<Workflow> findByWorkflowIdOrderByVersionDesc(String workflowId) {
            return store.values().stream()
                    .filter(workflow -> workflow.getWorkflowId().equals(workflowId))
                    .sorted(Comparator.comparing(Workflow::getVersion).reversed())
                    .toList();
        }

        private static String key(String workflowId, int version, WorkflowStatus status) {
            return workflowId + "::" + version + "::" + status.name();
        }
    }

    static final class InMemoryWorkflowExecutionRepository implements WorkflowExecutionRepository {

        private final Map<String, WorkflowExecution> executions = new LinkedHashMap<>();

        @Override
        public synchronized WorkflowExecution save(WorkflowExecution workflowExecution) {
            executions.put(workflowExecution.getExecutionId(), workflowExecution);
            return workflowExecution;
        }

        @Override
        public synchronized Optional<WorkflowExecution> findByExecutionId(String executionId) {
            return Optional.ofNullable(executions.get(executionId));
        }

        @Override
        public synchronized List<WorkflowExecution> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId) {
            return executions.values().stream()
                    .filter(execution -> execution.getWorkflowId().equals(workflowId))
                    .sorted(Comparator.comparing(WorkflowExecution::getUpdatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized Optional<WorkflowExecution> updateContextIncrementally(
                String executionId,
                Map<String, Object> contextPatch,
                String currentStateId,
                ExecutionStatus status,
                Instant updatedAt
        ) {
            WorkflowExecution existing = executions.get(executionId);
            if (existing == null) {
                return Optional.empty();
            }

            Map<String, Object> mergedData = new LinkedHashMap<>(existing.getContext().getData());
            if (contextPatch != null) {
                mergedData.putAll(contextPatch);
            }

            WorkflowExecution updated = new WorkflowExecution(
                    existing.getExecutionId(),
                    existing.getWorkflowId(),
                    existing.getWorkflowVersion(),
                    status,
                    currentStateId,
                    new WorkflowContext(existing.getExecutionId(), mergedData),
                    existing.getStartedAt(),
                    updatedAt
            );
            executions.put(executionId, updated);
            return Optional.of(updated);
        }
    }

    static final class InMemoryWorkflowEventLogRepository implements WorkflowEventLogRepository {

        @Override
        public synchronized com.executionpilot.engine.event.WorkflowEventLogEntry save(com.executionpilot.engine.event.WorkflowEventLogEntry eventLogEntry) {
            return eventLogEntry;
        }

        @Override
        public synchronized Optional<com.executionpilot.engine.event.WorkflowEventLogEntry> findByEventId(String eventId) {
            return Optional.empty();
        }

        @Override
        public synchronized List<com.executionpilot.engine.event.WorkflowEventLogEntry> findByExecutionIdOrderByCreatedAtAsc(String executionId) {
            return List.of();
        }
    }

    static final class InMemoryActionExecutionRepository implements ActionExecutionRepository {

        private final List<ActionExecutionRecord> records = new ArrayList<>();

        @Override
        public synchronized ActionExecutionRecord save(ActionExecutionRecord actionExecutionRecord) {
            for (int index = 0; index < records.size(); index++) {
                if (records.get(index).getActionExecutionId().equals(actionExecutionRecord.getActionExecutionId())) {
                    records.set(index, actionExecutionRecord);
                    return actionExecutionRecord;
                }
            }
            records.add(actionExecutionRecord);
            return actionExecutionRecord;
        }

        @Override
        public synchronized Optional<ActionExecutionRecord> findByActionExecutionId(String actionExecutionId) {
            return records.stream().filter(record -> record.getActionExecutionId().equals(actionExecutionId)).findFirst();
        }

        @Override
        public synchronized List<ActionExecutionRecord> findByExecutionIdOrderByStartedAtAsc(String executionId) {
            return records.stream()
                    .filter(record -> record.getExecutionId().equals(executionId))
                    .sorted(Comparator.comparing(ActionExecutionRecord::getStartedAt))
                    .toList();
        }

        @Override
        public synchronized List<ActionExecutionRecord> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId) {
            return records.stream()
                    .filter(record -> record.getExecutionId().equals(executionId) && record.getStateId().equals(stateId))
                    .sorted(Comparator.comparing(ActionExecutionRecord::getAttemptNo))
                    .toList();
        }
    }
}
