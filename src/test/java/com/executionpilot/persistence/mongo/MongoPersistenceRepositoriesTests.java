package com.executionpilot.persistence.mongo;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowEventLogRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
@ActiveProfiles("test")
@Import({
        MongoWorkflowDefinitionRepository.class,
        MongoWorkflowExecutionRepository.class,
        MongoActionExecutionRepository.class,
        MongoWorkflowEventLogRepository.class
})
class MongoPersistenceRepositoriesTests {

    private static final String TEST_MONGO_URI = System.getenv("TEST_MONGO_URI");

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Autowired
    private ActionExecutionRepository actionExecutionRepository;

    @Autowired
    private WorkflowEventLogRepository workflowEventLogRepository;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        if (hasExternalMongo()) {
            registry.add("spring.data.mongodb.uri", () -> TEST_MONGO_URI);
        }
    }

    @Test
    void savesAndLoadsWorkflowDefinitionsWithVersionAndStatus() {
        assumeTrue(hasExternalMongo(), "Set TEST_MONGO_URI to run Mongo repository integration tests.");
        Workflow draftV1 = sampleWorkflow("wf-orders", 1, WorkflowStatus.DRAFT);
        Workflow activeV1 = sampleWorkflow("wf-orders", 1, WorkflowStatus.ACTIVE);
        Workflow activeV2 = sampleWorkflow("wf-orders", 2, WorkflowStatus.ACTIVE);

        workflowDefinitionRepository.save(draftV1);
        workflowDefinitionRepository.save(activeV1);
        workflowDefinitionRepository.save(activeV2);

        assertTrue(workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndStatus("wf-orders", 1, WorkflowStatus.DRAFT).isPresent());
        assertTrue(workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndStatus("wf-orders", 1, WorkflowStatus.ACTIVE).isPresent());

        List<Workflow> versions = workflowDefinitionRepository.findByWorkflowIdOrderByVersionDesc("wf-orders");
        assertEquals(3, versions.size());
        assertEquals(2, versions.getFirst().getVersion());
    }

    @Test
    void updatesExecutionContextIncrementally() {
        assumeTrue(hasExternalMongo(), "Set TEST_MONGO_URI to run Mongo repository integration tests.");
        Instant now = Instant.now();
        WorkflowExecution execution = new WorkflowExecution(
                "exec-100",
                "wf-orders",
                1,
                ExecutionStatus.RUNNING,
                "start",
                new WorkflowContext("exec-100", Map.of("orderId", "ord-1")),
                now,
                now
        );
        workflowExecutionRepository.save(execution);

        workflowExecutionRepository.updateContextIncrementally(
                "exec-100",
                Map.of("paymentApproved", true, "attempt", 1),
                "payment",
                ExecutionStatus.WAITING,
                now.plusSeconds(5)
        );

        WorkflowExecution updated = workflowExecutionRepository.findByExecutionId("exec-100").orElseThrow();
        assertEquals("payment", updated.getCurrentStateId());
        assertEquals(ExecutionStatus.WAITING, updated.getStatus());
        assertEquals("ord-1", updated.getContext().getData().get("orderId"));
        assertEquals(true, updated.getContext().getData().get("paymentApproved"));
        assertEquals(1, updated.getContext().getData().get("attempt"));
    }

    @Test
    void storesActionAttemptsIndependentlyAndQueryable() {
        assumeTrue(hasExternalMongo(), "Set TEST_MONGO_URI to run Mongo repository integration tests.");
        Instant now = Instant.now();
        ActionExecutionRecord attempt1 = new ActionExecutionRecord(
                "act-1",
                "exec-200",
                "wf-orders",
                "payment",
                "action-pay",
                1,
                ActionExecutionStatus.FAILED,
                Map.of("amount", 99),
                null,
                "timeout",
                now,
                now.plusSeconds(1)
        );
        ActionExecutionRecord attempt2 = new ActionExecutionRecord(
                "act-2",
                "exec-200",
                "wf-orders",
                "payment",
                "action-pay",
                2,
                ActionExecutionStatus.SUCCESS,
                Map.of("amount", 99),
                Map.of("result", "ok"),
                "",
                now.plusSeconds(2),
                now.plusSeconds(3)
        );

        actionExecutionRepository.save(attempt1);
        actionExecutionRepository.save(attempt2);

        List<ActionExecutionRecord> records = actionExecutionRepository
                .findByExecutionIdAndStateIdOrderByAttemptNoAsc("exec-200", "payment");
        assertEquals(2, records.size());
        assertEquals(1, records.get(0).getAttemptNo());
        assertEquals(2, records.get(1).getAttemptNo());
    }

    @Test
    void storesEventLogsAndReturnsExecutionTimelineOrder() {
        assumeTrue(hasExternalMongo(), "Set TEST_MONGO_URI to run Mongo repository integration tests.");
        Instant now = Instant.now();
        workflowEventLogRepository.save(new WorkflowEventLogEntry(
                "evt-1",
                "exec-300",
                "wf-orders",
                "start",
                "STATE_STARTED",
                "Start entered",
                Map.of("state", "start"),
                now
        ));
        workflowEventLogRepository.save(new WorkflowEventLogEntry(
                "evt-2",
                "exec-300",
                "wf-orders",
                "end",
                "STATE_COMPLETED",
                "End completed",
                Map.of("state", "end"),
                now.plusSeconds(2)
        ));

        List<WorkflowEventLogEntry> timeline = workflowEventLogRepository.findByExecutionIdOrderByCreatedAtAsc("exec-300");
        assertEquals(2, timeline.size());
        assertEquals("evt-1", timeline.get(0).getEventId());
        assertEquals("evt-2", timeline.get(1).getEventId());
    }

    private static Workflow sampleWorkflow(String workflowId, int version, WorkflowStatus status) {
        RetryPolicy retryPolicy = new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(2), RetryBackoffType.EXPONENTIAL);
        ActionDefinition actionDefinition = new ActionDefinition(
                "action-1",
                "log",
                ActionType.LOG,
                Map.of("message", "hello"),
                retryPolicy
        );
        StateDefinition start = new StateDefinition("start", "Start", StateType.START, List.of(actionDefinition));
        StateDefinition end = new StateDefinition("end", "End", StateType.END, List.of());

        return new Workflow(
                workflowId,
                "Order Workflow",
                version,
                status,
                "start",
                "end",
                Map.of("start", start, "end", end),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );
    }

    private static boolean hasExternalMongo() {
        return TEST_MONGO_URI != null && !TEST_MONGO_URI.isBlank();
    }
}
