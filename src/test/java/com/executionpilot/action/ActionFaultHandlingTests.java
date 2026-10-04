package com.executionpilot.action;

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
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.engine.service.ActionExecutionService;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActionFaultHandlingTests {

    @Test
    void actionExecutionService_persistsRetryAndFinalSuccess() {
        AtomicInteger callCount = new AtomicInteger(0);
        ActionExecutor flakyExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                return callCount.incrementAndGet() == 1
                        ? ActionResult.retryableFailure("temporary outage")
                        : ActionResult.success(Map.of("result", "ok"));
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        InMemoryActionExecutionRepository repository = new InMemoryActionExecutionRepository();
        ActionExecutionService service = new ActionExecutionService(
                new ActionExecutorFactory(List.of(flakyExecutor)),
                RetryStrategyFactory.defaultFactory(),
                repository
        );

        ActionExecutionRecord finalRecord = service.executeAction(
                "exec-1",
                "wf-1",
                "state-1",
                actionDefinition(ActionType.LOG, retryPolicy(3), Map.of()),
                new WorkflowContext("exec-1", Map.of()),
                1
        );

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(finalRecord.getStatus()).isEqualTo(ActionExecutionStatus.SUCCESS);
        assertThat(finalRecord.getAttemptNo()).isEqualTo(2);
        assertThat(repository.records()).hasSize(2);
        assertThat(repository.records().get(0).getStatus()).isEqualTo(ActionExecutionStatus.RETRYING);
        assertThat(repository.records().get(0).getAttemptNo()).isEqualTo(1);
        assertThat(repository.records().get(1).getStatus()).isEqualTo(ActionExecutionStatus.SUCCESS);
        assertThat(repository.records().get(1).getAttemptNo()).isEqualTo(2);
    }

    @Test
    void actionExecutionService_marksFinalFailureAfterRetryExhaustion() {
        AtomicInteger callCount = new AtomicInteger(0);
        ActionExecutor alwaysFails = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                callCount.incrementAndGet();
                return ActionResult.retryableFailure("still failing");
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        InMemoryActionExecutionRepository repository = new InMemoryActionExecutionRepository();
        ActionExecutionService service = new ActionExecutionService(
                new ActionExecutorFactory(List.of(alwaysFails)),
                RetryStrategyFactory.defaultFactory(),
                repository
        );

        ActionExecutionRecord finalRecord = service.executeAction(
                "exec-2",
                "wf-2",
                "state-1",
                actionDefinition(ActionType.LOG, retryPolicy(3), Map.of()),
                new WorkflowContext("exec-2", Map.of()),
                1
        );

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(finalRecord.getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
        assertThat(repository.records()).hasSize(3);
        assertThat(repository.records()).extracting(ActionExecutionRecord::getStatus)
                .containsExactly(ActionExecutionStatus.RETRYING, ActionExecutionStatus.RETRYING, ActionExecutionStatus.FAILED);
    }

    @Test
    void actionExecutionService_stopsOnNonRetriableException() {
        AtomicInteger callCount = new AtomicInteger(0);
        ActionExecutor brokenExecutor = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext context) {
                callCount.incrementAndGet();
                throw ActionExecutionException.nonRetriable("invalid template");
            }

            @Override
            public boolean supports(ActionType type) {
                return type == ActionType.LOG;
            }
        };

        InMemoryActionExecutionRepository repository = new InMemoryActionExecutionRepository();
        ActionExecutionService service = new ActionExecutionService(
                new ActionExecutorFactory(List.of(brokenExecutor)),
                RetryStrategyFactory.defaultFactory(),
                repository
        );

        ActionExecutionRecord finalRecord = service.executeAction(
                "exec-3",
                "wf-3",
                "state-1",
                actionDefinition(ActionType.LOG, retryPolicy(3), Map.of("timeoutMs", "100")),
                new WorkflowContext("exec-3", Map.of()),
                1
        );

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(finalRecord.getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
        assertThat(finalRecord.getErrorMessage()).contains("invalid template");
        assertThat(repository.records()).hasSize(1);
    }

    @Test
    void actionExecutionService_retriesTimeoutFailuresUntilExhausted() {
        InMemoryActionExecutionRepository repository = new InMemoryActionExecutionRepository();
        ActionExecutionService service = new ActionExecutionService(
                new ActionExecutorFactory(List.of(new DelayActionExecutor())),
                RetryStrategyFactory.defaultFactory(),
                repository
        );

        ActionExecutionRecord finalRecord = service.executeAction(
                "exec-4",
                "wf-4",
                "state-1",
                actionDefinition(ActionType.WAIT, retryPolicy(2), Map.of("durationMs", "50", "timeoutMs", "5")),
                new WorkflowContext("exec-4", Map.of()),
                1
        );

        assertThat(finalRecord.getStatus()).isEqualTo(ActionExecutionStatus.FAILED);
        assertThat(finalRecord.getErrorMessage()).contains("timed out");
        assertThat(repository.records()).hasSize(2);
        assertThat(repository.records()).extracting(ActionExecutionRecord::getStatus)
                .containsExactly(ActionExecutionStatus.RETRYING, ActionExecutionStatus.FAILED);
    }

    private ActionDefinition actionDefinition(ActionType type, RetryPolicy retryPolicy, Map<String, Object> config) {
        return new ActionDefinition("action-1", "Action", type, config, retryPolicy);
    }

    private RetryPolicy retryPolicy(int maxAttempts) {
        return new RetryPolicy(maxAttempts, Duration.ofMillis(0), Duration.ofMillis(25), RetryBackoffType.FIXED_DELAY);
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
                    .toList();
        }

        @Override
        public List<ActionExecutionRecord> findByExecutionIdAndStateIdOrderByAttemptNoAsc(String executionId, String stateId) {
            return records.stream()
                    .filter(record -> record.getExecutionId().equals(executionId) && record.getStateId().equals(stateId))
                    .toList();
        }

        private List<ActionExecutionRecord> records() {
            return records;
        }
    }
}
