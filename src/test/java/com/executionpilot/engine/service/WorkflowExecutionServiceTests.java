package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutionException;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.factory.ActionExecutorFactory;
import com.executionpilot.action.retry.RetryStrategyFactory;
import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.state.DefaultWorkflowStateMachine;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionServiceTests {

    @Test
    void throwsWhenActiveWorkflowDefinitionIsMissing() {
        Workflow inactiveWorkflow = new Workflow(
                "wf-inactive",
                "Inactive Workflow",
                1,
                WorkflowStatus.DRAFT,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                new InMemoryWorkflowDefinitionRepository(inactiveWorkflow),
                new InMemoryWorkflowExecutionRepository(),
                new InMemoryWorkflowEventLogRepository(),
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(new SuccessfulLogExecutor())),
                        RetryStrategyFactory.defaultFactory(),
                        new InMemoryActionExecutionRepository()
                ),
                new DefaultWorkflowStateMachine()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                workflowExecutionService.executeWorkflow("wf-inactive", 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Active workflow definition not found");
    }

    @Test
    void executesWorkflowSuccessfullyFromStartToEnd() {
        Workflow workflow = successfulWorkflow();
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository(workflow);
        InMemoryWorkflowExecutionRepository workflowExecutionRepository = new InMemoryWorkflowExecutionRepository();
        InMemoryWorkflowEventLogRepository workflowEventLogRepository = new InMemoryWorkflowEventLogRepository();
        InMemoryActionExecutionRepository actionExecutionRepository = new InMemoryActionExecutionRepository();

        ActionExecutionService actionExecutionService = new ActionExecutionService(
                new ActionExecutorFactory(List.of(new SuccessfulLogExecutor())),
                RetryStrategyFactory.defaultFactory(),
                actionExecutionRepository
        );

        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                workflowDefinitionRepository,
                workflowExecutionRepository,
                workflowEventLogRepository,
                actionExecutionService,
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow(
                workflow.getWorkflowId(),
                workflow.getVersion(),
                Map.of("orderId", "ord-1")
        );

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.getCurrentStateId()).isEqualTo("end");
        assertThat(result.getContext().getData()).containsEntry("orderId", "ord-1");
        assertThat(result.getContext().getData()).containsEntry("lastCompletedStateId", "end");
        assertThat(result.getContext().getData()).containsKey("actionOutputs");
        assertThat(actionExecutionRepository.records).hasSize(2);
        assertThat(workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains(
                        "STATE_STARTED",
                        "ACTION_SUCCEEDED",
                        "STATE_COMPLETED",
                        "WORKFLOW_COMPLETED"
                );
    }

    @Test
    void failsWorkflowMidFlowWhenActionFailsNonRetriably() {
        Workflow workflow = workflowWithMidState();
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository(workflow);
        InMemoryWorkflowExecutionRepository workflowExecutionRepository = new InMemoryWorkflowExecutionRepository();
        InMemoryWorkflowEventLogRepository workflowEventLogRepository = new InMemoryWorkflowEventLogRepository();
        InMemoryActionExecutionRepository actionExecutionRepository = new InMemoryActionExecutionRepository();

        ActionExecutor failingExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                if ("action-ship".equals(context.getActionDefinition().getActionId())) {
                    throw ActionExecutionException.nonRetriable("shipping rule invalid");
                }
                return ActionResult.success(Map.of("actionId", context.getActionDefinition().getActionId()));
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                workflowDefinitionRepository,
                workflowExecutionRepository,
                workflowEventLogRepository,
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(failingExecutor)),
                        RetryStrategyFactory.defaultFactory(),
                        actionExecutionRepository
                ),
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow(
                workflow.getWorkflowId(),
                workflow.getVersion(),
                Map.of("shipmentId", "sh-1")
        );

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getCurrentStateId()).isEqualTo("middle");
        assertThat(result.getContext().getData()).containsEntry("failureActionId", "action-ship");
        assertThat(workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains("ACTION_FAILED", "WORKFLOW_FAILED");
    }

    @Test
    void recoversFromRetryAndCompletesWorkflow() {
        Workflow workflow = successfulWorkflow();
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository(workflow);
        InMemoryWorkflowExecutionRepository workflowExecutionRepository = new InMemoryWorkflowExecutionRepository();
        InMemoryWorkflowEventLogRepository workflowEventLogRepository = new InMemoryWorkflowEventLogRepository();
        InMemoryActionExecutionRepository actionExecutionRepository = new InMemoryActionExecutionRepository();
        AtomicInteger startActionAttempts = new AtomicInteger(0);

        ActionExecutor flakyExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                if ("action-start".equals(context.getActionDefinition().getActionId())
                        && startActionAttempts.incrementAndGet() == 1) {
                    return ActionResult.retryableFailure("temporary dependency outage");
                }
                return ActionResult.success(Map.of("actionId", context.getActionDefinition().getActionId()));
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                workflowDefinitionRepository,
                workflowExecutionRepository,
                workflowEventLogRepository,
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(flakyExecutor)),
                        RetryStrategyFactory.defaultFactory(),
                        actionExecutionRepository
                ),
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow(
                workflow.getWorkflowId(),
                workflow.getVersion(),
                Map.of("invoiceId", "inv-1")
        );

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(startActionAttempts.get()).isEqualTo(2);
        assertThat(actionExecutionRepository.records)
                .extracting(ActionExecutionRecord::getAttemptNo)
                .contains(1, 2);
        assertThat(workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains("RETRY_SCHEDULED", "WORKFLOW_COMPLETED");
    }

    @Test
    void completesWorkflowWhenStartStateHasNoActions() {
        Workflow workflow = new Workflow(
                "wf-no-actions",
                "No Actions Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        InMemoryWorkflowEventLogRepository eventLogRepository = new InMemoryWorkflowEventLogRepository();
        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                new InMemoryWorkflowDefinitionRepository(workflow),
                new InMemoryWorkflowExecutionRepository(),
                eventLogRepository,
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(new SuccessfulLogExecutor())),
                        RetryStrategyFactory.defaultFactory(),
                        new InMemoryActionExecutionRepository()
                ),
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow("wf-no-actions", 1, Map.of("seed", "x"));

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.getCurrentStateId()).isEqualTo("end");
        assertThat(eventLogRepository.events).extracting(WorkflowEventLogEntry::getEventType)
                .contains("STATE_STARTED", "STATE_COMPLETED", "WORKFLOW_COMPLETED");
    }

    @Test
    void failsWorkflowWhenStateHasNoOutgoingTransition() {
        RetryPolicy retryPolicy = new RetryPolicy(1, Duration.ZERO, Duration.ZERO, RetryBackoffType.FIXED_DELAY);
        Workflow workflow = new Workflow(
                "wf-no-transition",
                "No Transition Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of(
                                new ActionDefinition("action-start", "Start Action", ActionType.LOG, Map.of(), retryPolicy)
                        )),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of()
        );

        InMemoryWorkflowEventLogRepository eventLogRepository = new InMemoryWorkflowEventLogRepository();
        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                new InMemoryWorkflowDefinitionRepository(workflow),
                new InMemoryWorkflowExecutionRepository(),
                eventLogRepository,
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(new SuccessfulLogExecutor())),
                        RetryStrategyFactory.defaultFactory(),
                        new InMemoryActionExecutionRepository()
                ),
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow("wf-no-transition", 1, Map.of());

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getCurrentStateId()).isEqualTo("start");
        assertThat(eventLogRepository.events).extracting(WorkflowEventLogEntry::getEventType)
                .contains("WORKFLOW_FAILED");
    }

    @Test
    void failsWorkflowWhenStateHasMultipleOutgoingTransitions() {
        RetryPolicy retryPolicy = new RetryPolicy(1, Duration.ZERO, Duration.ZERO, RetryBackoffType.FIXED_DELAY);
        Workflow workflow = new Workflow(
                "wf-branching",
                "Branching Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of(
                                new ActionDefinition("action-start", "Start Action", ActionType.LOG, Map.of(), retryPolicy)
                        )),
                        "mid", new StateDefinition("mid", "Mid", StateType.NORMAL, List.of()),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(
                        new TransitionDefinition("t1", "start", "mid", "STEP", ""),
                        new TransitionDefinition("t2", "start", "end", "DONE", "")
                )
        );

        InMemoryWorkflowEventLogRepository eventLogRepository = new InMemoryWorkflowEventLogRepository();
        WorkflowExecutionService workflowExecutionService = new WorkflowExecutionService(
                new InMemoryWorkflowDefinitionRepository(workflow),
                new InMemoryWorkflowExecutionRepository(),
                eventLogRepository,
                new ActionExecutionService(
                        new ActionExecutorFactory(List.of(new SuccessfulLogExecutor())),
                        RetryStrategyFactory.defaultFactory(),
                        new InMemoryActionExecutionRepository()
                ),
                new DefaultWorkflowStateMachine()
        );

        WorkflowExecution result = workflowExecutionService.executeWorkflow("wf-branching", 1, Map.of());

        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.getCurrentStateId()).isEqualTo("start");
        assertThat(eventLogRepository.events).extracting(WorkflowEventLogEntry::getEventType)
                .contains("WORKFLOW_FAILED");
    }

    private static Workflow successfulWorkflow() {
        RetryPolicy retryPolicy = new RetryPolicy(2, Duration.ZERO, Duration.ofMillis(5), RetryBackoffType.FIXED_DELAY);
        ActionDefinition startAction = new ActionDefinition("action-start", "Start Action", ActionType.LOG, Map.of(), retryPolicy);
        ActionDefinition endAction = new ActionDefinition("action-end", "End Action", ActionType.LOG, Map.of(), retryPolicy);
        StateDefinition start = new StateDefinition("start", "Start", StateType.START, List.of(startAction));
        StateDefinition end = new StateDefinition("end", "End", StateType.END, List.of(endAction));
        return new Workflow(
                "wf-success",
                "Successful Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of("start", start, "end", end),
                List.of(new TransitionDefinition("t-start-end", "start", "end", "DONE", ""))
        );
    }

    private static Workflow workflowWithMidState() {
        RetryPolicy retryPolicy = new RetryPolicy(2, Duration.ZERO, Duration.ofMillis(5), RetryBackoffType.FIXED_DELAY);
        ActionDefinition startAction = new ActionDefinition("action-start", "Start Action", ActionType.LOG, Map.of(), retryPolicy);
        ActionDefinition midAction = new ActionDefinition("action-ship", "Ship Action", ActionType.LOG, Map.of(), retryPolicy);
        ActionDefinition endAction = new ActionDefinition("action-end", "End Action", ActionType.LOG, Map.of(), retryPolicy);
        StateDefinition start = new StateDefinition("start", "Start", StateType.START, List.of(startAction));
        StateDefinition middle = new StateDefinition("middle", "Middle", StateType.NORMAL, List.of(midAction));
        StateDefinition end = new StateDefinition("end", "End", StateType.END, List.of(endAction));
        return new Workflow(
                "wf-mid-fail",
                "Mid Failure Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of("start", start, "middle", middle, "end", end),
                List.of(
                        new TransitionDefinition("t-start-middle", "start", "middle", "DONE", ""),
                        new TransitionDefinition("t-middle-end", "middle", "end", "DONE", "")
                )
        );
    }

    private static final class SuccessfulLogExecutor implements ActionExecutor {

        @Override
        public ActionResult execute(ActionContext context) {
            return ActionResult.success(Map.of("actionId", context.getActionDefinition().getActionId()));
        }

        @Override
        public boolean supports(ActionType type) {
            return type == ActionType.LOG;
        }
    }

    private static final class InMemoryWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

        private final Workflow workflow;

        private InMemoryWorkflowDefinitionRepository(Workflow workflow) {
            this.workflow = workflow;
        }

        @Override
        public Workflow save(Workflow workflow) {
            throw new UnsupportedOperationException("save not needed for this test");
        }

        @Override
        public Optional<Workflow> findByWorkflowIdAndVersion(String workflowId, int version) {
            if (workflow.getWorkflowId().equals(workflowId) && workflow.getVersion() == version) {
                return Optional.of(workflow);
            }
            return Optional.empty();
        }

        @Override
        public Optional<Workflow> findByWorkflowIdAndVersionAndStatus(String workflowId, int version, WorkflowStatus status) {
            if (workflow.getWorkflowId().equals(workflowId)
                    && workflow.getVersion() == version
                    && workflow.getStatus() == status) {
                return Optional.of(workflow);
            }
            return Optional.empty();
        }

        @Override
        public List<Workflow> findByWorkflowIdOrderByVersionDesc(String workflowId) {
            return workflow.getWorkflowId().equals(workflowId) ? List.of(workflow) : List.of();
        }
    }

    private static final class InMemoryWorkflowExecutionRepository implements WorkflowExecutionRepository {

        private final Map<String, WorkflowExecution> executions = new LinkedHashMap<>();

        @Override
        public WorkflowExecution save(WorkflowExecution workflowExecution) {
            executions.put(workflowExecution.getExecutionId(), workflowExecution);
            return workflowExecution;
        }

        @Override
        public Optional<WorkflowExecution> findByExecutionId(String executionId) {
            return Optional.ofNullable(executions.get(executionId));
        }

        @Override
        public List<WorkflowExecution> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId) {
            return executions.values().stream()
                    .filter(execution -> execution.getWorkflowId().equals(workflowId))
                    .sorted(Comparator.comparing(WorkflowExecution::getUpdatedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<WorkflowExecution> updateContextIncrementally(
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

    private static final class InMemoryWorkflowEventLogRepository implements WorkflowEventLogRepository {

        private final List<WorkflowEventLogEntry> events = new ArrayList<>();

        @Override
        public WorkflowEventLogEntry save(WorkflowEventLogEntry eventLogEntry) {
            events.add(eventLogEntry);
            return eventLogEntry;
        }

        @Override
        public Optional<WorkflowEventLogEntry> findByEventId(String eventId) {
            return events.stream().filter(event -> event.getEventId().equals(eventId)).findFirst();
        }

        @Override
        public List<WorkflowEventLogEntry> findByExecutionIdOrderByCreatedAtAsc(String executionId) {
            return events.stream()
                    .filter(event -> event.getExecutionId().equals(executionId))
                    .sorted(Comparator.comparing(WorkflowEventLogEntry::getCreatedAt))
                    .toList();
        }
    }

    private static final class InMemoryActionExecutionRepository implements ActionExecutionRepository {

        private final List<ActionExecutionRecord> records = new ArrayList<>();

        @Override
        public ActionExecutionRecord save(ActionExecutionRecord actionExecutionRecord) {
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
        public Optional<ActionExecutionRecord> findByActionExecutionId(String actionExecutionId) {
            return records.stream()
                    .filter(record -> record.getActionExecutionId().equals(actionExecutionId))
                    .findFirst();
        }

        @Override
        public List<ActionExecutionRecord> findByExecutionIdOrderByStartedAtAsc(String executionId) {
            return records.stream()
                    .filter(record -> record.getExecutionId().equals(executionId))
                    .sorted(Comparator.comparing(ActionExecutionRecord::getStartedAt))
                    .toList();
        }

        @Override
        public List<ActionExecutionRecord> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId) {
            return records.stream()
                    .filter(record -> record.getExecutionId().equals(executionId) && record.getStateId().equals(stateId))
                    .sorted(Comparator.comparing(ActionExecutionRecord::getAttemptNo))
                    .toList();
        }
    }
}
