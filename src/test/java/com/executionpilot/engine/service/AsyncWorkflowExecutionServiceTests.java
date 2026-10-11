package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutionException;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.executor.impl.DelayActionExecutor;
import com.executionpilot.action.factory.ActionExecutorFactory;
import com.executionpilot.action.retry.RetryStrategyFactory;
import com.executionpilot.engine.event.EventBus;
import com.executionpilot.engine.event.InMemoryEventProcessingTracker;
import com.executionpilot.engine.event.InProcessEventBus;
import com.executionpilot.engine.event.StateCompletionSignalHandler;
import com.executionpilot.engine.event.StateExecutionSignalHandler;
import com.executionpilot.engine.event.WorkflowAsyncSignalLogHandler;
import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.state.DefaultWorkflowStateMachine;
import com.executionpilot.engine.state.WorkflowStateMachine;
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

class AsyncWorkflowExecutionServiceTests {

    @Test
    void startsWorkflowAsynchronouslyWithoutBlockingCaller() {
        RetryPolicy retryPolicy = new RetryPolicy(1, Duration.ZERO, Duration.ZERO, RetryBackoffType.FIXED_DELAY);
        Workflow workflow = new Workflow(
                "wf-async-wait",
                "Async Wait Flow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of(
                                new ActionDefinition("wait-action", "Wait", ActionType.WAIT, Map.of("durationMs", "200"), retryPolicy)
                        )),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );

        RuntimeHarness harness = runtimeHarness(workflow, List.of(new DelayActionExecutor()));

        long startNanos = System.nanoTime();
        String executionId = harness.asyncWorkflowExecutionService.startWorkflowAsync("wf-async-wait", 1, Map.of("traceId", "t-1"));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertThat(executionId).isNotBlank();
        assertThat(elapsedMillis).isLessThan(100L);

        WorkflowExecution completed = waitForTerminalExecution(harness.workflowExecutionRepository, executionId);
        assertThat(completed.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(harness.workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains("STATE_STARTED", "STATE_COMPLETED", "WORKFLOW_COMPLETED");
    }

    @Test
    void retriesAndCompletesAsynchronously() {
        Workflow workflow = asyncLogWorkflow("wf-async-retry");
        AtomicInteger attempts = new AtomicInteger(0);
        ActionExecutor flakyExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                if (attempts.incrementAndGet() == 1) {
                    return ActionResult.retryableFailure("temporary network issue");
                }
                return ActionResult.success(Map.of("actionId", context.getActionDefinition().getActionId()));
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        RuntimeHarness harness = runtimeHarness(workflow, List.of(flakyExecutor));
        String executionId = harness.asyncWorkflowExecutionService.startWorkflowAsync(workflow.getWorkflowId(), workflow.getVersion(), Map.of());

        WorkflowExecution completed = waitForTerminalExecution(harness.workflowExecutionRepository, executionId);
        assertThat(completed.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(harness.actionExecutionRepository.records).hasSize(2);
        assertThat(harness.workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains("RETRY_SCHEDULED", "ACTION_SUCCEEDED", "WORKFLOW_COMPLETED");
    }

    @Test
    void failsAsynchronouslyAndPublishesFailureEvents() {
        Workflow workflow = asyncLogWorkflow("wf-async-fail");
        ActionExecutor failingExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                throw ActionExecutionException.nonRetriable("invalid downstream request");
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        RuntimeHarness harness = runtimeHarness(workflow, List.of(failingExecutor));
        String executionId = harness.asyncWorkflowExecutionService.startWorkflowAsync(workflow.getWorkflowId(), workflow.getVersion(), Map.of());

        WorkflowExecution failed = waitForTerminalExecution(harness.workflowExecutionRepository, executionId);
        assertThat(failed.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failed.getContext().getData()).containsEntry("failureActionId", "log-action");
        assertThat(harness.workflowEventLogRepository.events)
                .extracting(WorkflowEventLogEntry::getEventType)
                .contains("ACTION_FAILED", "WORKFLOW_FAILED");
    }

    private static Workflow asyncLogWorkflow(String workflowId) {
        RetryPolicy retryPolicy = new RetryPolicy(2, Duration.ZERO, Duration.ofMillis(5), RetryBackoffType.FIXED_DELAY);
        return new Workflow(
                workflowId,
                "Async Log Workflow",
                1,
                WorkflowStatus.ACTIVE,
                "start",
                "end",
                Map.of(
                        "start", new StateDefinition("start", "Start", StateType.START, List.of(
                                new ActionDefinition("log-action", "Log", ActionType.LOG, Map.of(), retryPolicy)
                        )),
                        "end", new StateDefinition("end", "End", StateType.END, List.of())
                ),
                List.of(new TransitionDefinition("t1", "start", "end", "DONE", ""))
        );
    }

    private static RuntimeHarness runtimeHarness(Workflow workflow, List<ActionExecutor> executors) {
        InMemoryWorkflowDefinitionRepository workflowDefinitionRepository = new InMemoryWorkflowDefinitionRepository(workflow);
        InMemoryWorkflowExecutionRepository workflowExecutionRepository = new InMemoryWorkflowExecutionRepository();
        InMemoryWorkflowEventLogRepository workflowEventLogRepository = new InMemoryWorkflowEventLogRepository();
        InMemoryActionExecutionRepository actionExecutionRepository = new InMemoryActionExecutionRepository();
        WorkflowStateMachine workflowStateMachine = new DefaultWorkflowStateMachine();
        EventBus eventBus = new InProcessEventBus(new InMemoryEventProcessingTracker());

        ActionExecutionService actionExecutionService = new ActionExecutionService(
                new ActionExecutorFactory(executors),
                RetryStrategyFactory.defaultFactory(),
                actionExecutionRepository
        );
        AsyncActionExecutionDispatcher asyncActionExecutionDispatcher = new AsyncActionExecutionDispatcher(actionExecutionService);
        new WorkflowAsyncSignalLogHandler(eventBus, workflowEventLogRepository);
        new StateCompletionSignalHandler(
                eventBus,
                workflowDefinitionRepository,
                workflowExecutionRepository,
                workflowStateMachine
        );
        new StateExecutionSignalHandler(
                eventBus,
                workflowDefinitionRepository,
                workflowExecutionRepository,
                asyncActionExecutionDispatcher,
                workflowStateMachine
        );
        AsyncWorkflowExecutionService asyncWorkflowExecutionService = new AsyncWorkflowExecutionService(
                workflowDefinitionRepository,
                workflowExecutionRepository,
                workflowStateMachine,
                eventBus
        );
        return new RuntimeHarness(
                asyncWorkflowExecutionService,
                workflowExecutionRepository,
                workflowEventLogRepository,
                actionExecutionRepository
        );
    }

    private static WorkflowExecution waitForTerminalExecution(
            InMemoryWorkflowExecutionRepository workflowExecutionRepository,
            String executionId
    ) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            WorkflowExecution execution = workflowExecutionRepository.findByExecutionId(executionId).orElseThrow();
            if (execution.getStatus() == ExecutionStatus.COMPLETED || execution.getStatus() == ExecutionStatus.FAILED) {
                return execution;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for async execution to finish.", exception);
            }
        }
        throw new AssertionError("Async workflow execution did not reach terminal state within timeout.");
    }

    private record RuntimeHarness(
            AsyncWorkflowExecutionService asyncWorkflowExecutionService,
            InMemoryWorkflowExecutionRepository workflowExecutionRepository,
            InMemoryWorkflowEventLogRepository workflowEventLogRepository,
            InMemoryActionExecutionRepository actionExecutionRepository
    ) {
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

    private static final class InMemoryWorkflowEventLogRepository implements WorkflowEventLogRepository {

        private final List<WorkflowEventLogEntry> events = new ArrayList<>();

        @Override
        public synchronized WorkflowEventLogEntry save(WorkflowEventLogEntry eventLogEntry) {
            events.add(eventLogEntry);
            return eventLogEntry;
        }

        @Override
        public synchronized Optional<WorkflowEventLogEntry> findByEventId(String eventId) {
            return events.stream().filter(event -> event.getEventId().equals(eventId)).findFirst();
        }

        @Override
        public synchronized List<WorkflowEventLogEntry> findByExecutionIdOrderByCreatedAtAsc(String executionId) {
            return events.stream()
                    .filter(event -> event.getExecutionId().equals(executionId))
                    .sorted(Comparator.comparing(WorkflowEventLogEntry::getCreatedAt))
                    .toList();
        }
    }

    private static final class InMemoryActionExecutionRepository implements ActionExecutionRepository {

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
            return records.stream()
                    .filter(record -> record.getActionExecutionId().equals(actionExecutionId))
                    .findFirst();
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
