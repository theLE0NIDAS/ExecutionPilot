package com.executionpilot.action;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionExecutionException;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.executor.command.ActionCommand;
import com.executionpilot.action.executor.decorator.LoggingActionExecutorDecorator;
import com.executionpilot.action.executor.decorator.RetryActionExecutorDecorator;
import com.executionpilot.action.executor.decorator.TimeoutActionExecutorDecorator;
import com.executionpilot.action.executor.impl.CustomActionExecutor;
import com.executionpilot.action.executor.impl.DelayActionExecutor;
import com.executionpilot.action.executor.impl.LogActionExecutor;
import com.executionpilot.action.factory.ActionExecutorFactory;
import com.executionpilot.action.retry.RetryStrategyFactory;
import com.executionpilot.engine.execution.WorkflowContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ActionExecutionFrameworkTests {

    private WorkflowContext workflowContext;

    @BeforeEach
    void setUp() {
        workflowContext = new WorkflowContext("exec-1", Map.of());
    }

    // ---- helpers ----

    private ActionDefinition logAction(Map<String, Object> config) {
        return new ActionDefinition("action-log", "Log Message", ActionType.LOG, config, noRetry());
    }

    private ActionDefinition waitAction(Map<String, Object> config) {
        return new ActionDefinition("action-wait", "Wait", ActionType.WAIT, config, noRetry());
    }

    private ActionDefinition customAction() {
        return new ActionDefinition("action-custom", "Custom", ActionType.CUSTOM, Map.of(), noRetry());
    }

    private RetryPolicy noRetry() {
        return new RetryPolicy(1, Duration.ofMillis(0), Duration.ofMillis(0), RetryBackoffType.FIXED_DELAY);
    }

    private RetryPolicy retryThrice() {
        return new RetryPolicy(3, Duration.ofMillis(5), Duration.ofMillis(50), RetryBackoffType.FIXED_DELAY);
    }

    private ActionContext contextFor(ActionDefinition def) {
        return new ActionContext("exec-1", "wf-1", "state-1", def, workflowContext);
    }

    // ---- LogActionExecutor ----

    @Test
    void logActionExecutor_returnsSuccess() {
        LogActionExecutor executor = new LogActionExecutor();
        ActionContext ctx = contextFor(logAction(Map.of("message", "hello test")));
        ActionResult result = executor.execute(ctx);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("hello test");
    }

    @Test
    void logActionExecutor_supportsLogType() {
        LogActionExecutor executor = new LogActionExecutor();
        assertThat(executor.supports(ActionType.LOG)).isTrue();
        assertThat(executor.supports(ActionType.HTTP_CALL)).isFalse();
    }

    // ---- DelayActionExecutor ----

    @Test
    void delayActionExecutor_waitsAndReturnsSuccess() {
        DelayActionExecutor executor = new DelayActionExecutor();
        ActionContext ctx = contextFor(waitAction(Map.of("durationMs", "10")));
        long start = System.currentTimeMillis();
        ActionResult result = executor.execute(ctx);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(result.isSuccess()).isTrue();
        assertThat(elapsed).isGreaterThanOrEqualTo(10L);
    }

    @Test
    void delayActionExecutor_failsOnMissingDuration() {
        DelayActionExecutor executor = new DelayActionExecutor();
        ActionContext ctx = contextFor(waitAction(Map.of()));
        ActionResult result = executor.execute(ctx);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("durationMs");
    }

    @Test
    void delayActionExecutor_failsOnInvalidDuration() {
        DelayActionExecutor executor = new DelayActionExecutor();
        ActionContext ctx = contextFor(waitAction(Map.of("durationMs", "notanumber")));
        ActionResult result = executor.execute(ctx);
        assertThat(result.isSuccess()).isFalse();
    }

    // ---- CustomActionExecutor ----

    @Test
    void customActionExecutor_returnsSuccessPlaceholder() {
        CustomActionExecutor executor = new CustomActionExecutor();
        ActionContext ctx = contextFor(customAction());
        ActionResult result = executor.execute(ctx);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void customActionExecutor_supportsBothCustomAndScript() {
        CustomActionExecutor executor = new CustomActionExecutor();
        assertThat(executor.supports(ActionType.CUSTOM)).isTrue();
        assertThat(executor.supports(ActionType.SCRIPT)).isTrue();
    }

    // ---- ActionExecutorFactory ----

    @Test
    void factory_resolvesCorrectExecutor() {
        LogActionExecutor logExecutor = new LogActionExecutor();
        DelayActionExecutor delayExecutor = new DelayActionExecutor();
        ActionExecutorFactory factory = new ActionExecutorFactory(List.of(logExecutor, delayExecutor));

        assertThat(factory.getExecutor(ActionType.LOG)).isSameAs(logExecutor);
        assertThat(factory.getExecutor(ActionType.WAIT)).isSameAs(delayExecutor);
    }

    @Test
    void factory_throwsWhenNoExecutorRegistered() {
        ActionExecutorFactory factory = new ActionExecutorFactory(List.of(new LogActionExecutor()));
        assertThatThrownBy(() -> factory.getExecutor(ActionType.HTTP_CALL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP_CALL");
    }

    // ---- ActionCommand ----

    @Test
    void actionCommand_invokesExecutorAndReturnsResult() {
        LogActionExecutor executor = new LogActionExecutor();
        ActionContext ctx = contextFor(logAction(Map.of("message", "command test")));
        ActionCommand command = new ActionCommand(ctx, executor);
        ActionResult result = command.execute();
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- LoggingActionExecutorDecorator ----

    @Test
    void loggingDecorator_propagatesResult() {
        LogActionExecutor core = new LogActionExecutor();
        LoggingActionExecutorDecorator decorated = new LoggingActionExecutorDecorator(core);
        ActionContext ctx = contextFor(logAction(Map.of("message", "decorated log")));
        ActionResult result = decorated.execute(ctx);
        assertThat(result.isSuccess()).isTrue();
    }

    // ---- RetryActionExecutorDecorator ----

    @Test
    void retryDecorator_succeedsOnFirstAttempt() {
        LogActionExecutor core = new LogActionExecutor();
        RetryActionExecutorDecorator decorated = new RetryActionExecutorDecorator(core, RetryStrategyFactory.defaultFactory());
        ActionDefinition def = new ActionDefinition("a1", "Log", ActionType.LOG,
                Map.of("message", "retry test"), retryThrice());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);
        ActionResult result = decorated.execute(ctx);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void retryDecorator_retriesOnFailureAndEventuallyFails() {
        AtomicInteger callCount = new AtomicInteger(0);
        ActionExecutor alwaysFails = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext ctx) {
                callCount.incrementAndGet();
                return ActionResult.retryableFailure("simulated failure");
            }

            @Override
            public boolean supports(ActionType type) {
                return true;
            }
        };

        ActionDefinition def = new ActionDefinition("a2", "Fail", ActionType.LOG,
                Map.of("message", "fail"), retryThrice());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);

        RetryActionExecutorDecorator decorated = new RetryActionExecutorDecorator(alwaysFails, RetryStrategyFactory.defaultFactory());
        ActionResult result = decorated.execute(ctx);

        assertThat(result.isSuccess()).isFalse();
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void retryDecorator_succeedsOnSecondAttempt() {
        AtomicInteger callCount = new AtomicInteger(0);
        ActionExecutor failOnceThenSucceed = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext ctx) {
                int count = callCount.incrementAndGet();
                return count == 1 ? ActionResult.retryableFailure("first fail") : ActionResult.success("ok");
            }

            @Override
            public boolean supports(ActionType type) {
                return true;
            }
        };

        ActionDefinition def = new ActionDefinition("a3", "Retry Once", ActionType.LOG,
                Map.of("message", "retry"), retryThrice());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);

        RetryActionExecutorDecorator decorated = new RetryActionExecutorDecorator(failOnceThenSucceed, RetryStrategyFactory.defaultFactory());
        ActionResult result = decorated.execute(ctx);

        assertThat(result.isSuccess()).isTrue();
        assertThat(callCount.get()).isEqualTo(2);
    }

    // ---- TimeoutActionExecutorDecorator ----

    @Test
    void timeoutDecorator_returnsResultWhenWithinTimeout() {
        LogActionExecutor core = new LogActionExecutor();
        TimeoutActionExecutorDecorator decorated = new TimeoutActionExecutorDecorator(core);
        ActionDefinition def = new ActionDefinition("a4", "Fast", ActionType.LOG,
                Map.of("message", "fast", "timeoutMs", "5000"), noRetry());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);
        ActionResult result = decorated.execute(ctx);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void timeoutDecorator_failsWhenExecutionExceedsTimeout() {
        DelayActionExecutor core = new DelayActionExecutor();
        TimeoutActionExecutorDecorator decorated = new TimeoutActionExecutorDecorator(core);
        // 500ms delay but 50ms timeout
        ActionDefinition def = new ActionDefinition("a5", "Slow", ActionType.WAIT,
                Map.of("durationMs", "500", "timeoutMs", "50"), noRetry());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);
        ActionResult result = decorated.execute(ctx);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetriable()).isTrue();
        assertThat(result.getErrorMessage()).contains("timed out");
    }

    @Test
    void timeoutDecorator_stopsOnNonRetriableException() {
        ActionExecutor core = new ActionExecutor() {
            @Override
            public ActionResult execute(ActionContext ctx) {
                throw ActionExecutionException.nonRetriable("bad config");
            }

            @Override
            public boolean supports(ActionType type) {
                return true;
            }
        };

        TimeoutActionExecutorDecorator decorated = new TimeoutActionExecutorDecorator(core);
        ActionDefinition def = new ActionDefinition("a6", "Broken", ActionType.LOG,
                Map.of("timeoutMs", "1000"), noRetry());
        ActionContext ctx = new ActionContext("e1", "w1", "s1", def, workflowContext);
        ActionResult result = decorated.execute(ctx);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isRetriable()).isFalse();
        assertThat(result.getErrorMessage()).contains("bad config");
    }
}
