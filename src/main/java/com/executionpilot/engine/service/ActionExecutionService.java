package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.executor.command.ActionCommand;
import com.executionpilot.action.executor.decorator.LoggingActionExecutorDecorator;
import com.executionpilot.action.executor.decorator.TimeoutActionExecutorDecorator;
import com.executionpilot.action.factory.ActionExecutorFactory;
import com.executionpilot.action.retry.RetryStrategy;
import com.executionpilot.action.retry.RetryStrategyFactory;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.ActionExecutionStatus;
import com.executionpilot.engine.execution.WorkflowContext;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the full lifecycle of a single action execution:
 * 1. Resolves the executor from factory
 * 2. Wraps it in Logging → Retry → Timeout decorator chain
 * 3. Builds and invokes an ActionCommand
 * 4. Persists the result as an ActionExecutionRecord
 */
@Service
public class ActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutionService.class);

    private final ActionExecutorFactory executorFactory;
    private final RetryStrategyFactory retryStrategyFactory;
    private final ActionExecutionRepository actionExecutionRepository;

    public ActionExecutionService(
            ActionExecutorFactory executorFactory,
            RetryStrategyFactory retryStrategyFactory,
            ActionExecutionRepository actionExecutionRepository
    ) {
        this.executorFactory = executorFactory;
        this.retryStrategyFactory = retryStrategyFactory;
        this.actionExecutionRepository = actionExecutionRepository;
    }

    /**
     * Executes a single action, persists the result, and returns the ActionExecutionRecord.
     *
     * @param executionId     the workflow execution id
     * @param workflowId      the workflow definition id
     * @param stateId         the state in which this action is executing
     * @param actionDef       the action definition to execute
     * @param workflowContext the live workflow context
     * @param attemptNo       current attempt number (1-based); callers pass 1 for first attempt
     * @return the persisted ActionExecutionRecord
     */
    public ActionExecutionRecord executeAction(
            String executionId,
            String workflowId,
            String stateId,
            ActionDefinition actionDef,
            WorkflowContext workflowContext,
            int startingAttemptNo
    ) {
        ActionExecutor rawExecutor = executorFactory.getExecutor(actionDef.getType());
        ActionExecutor decorated = new LoggingActionExecutorDecorator(new TimeoutActionExecutorDecorator(rawExecutor));
        RetryStrategy retryStrategy = retryStrategyFactory.getStrategy(actionDef.getRetryPolicy().getBackoffType());

        ActionContext context = new ActionContext(executionId, workflowId, stateId, actionDef, workflowContext);
        ActionCommand command = new ActionCommand(context, decorated);
        int maxAttempts = actionDef.getRetryPolicy().getMaxAttempts();

        for (int offset = 0; offset < maxAttempts; offset++) {
            int attemptNo = startingAttemptNo + offset;
            Instant start = Instant.now();
            ActionResult result = command.execute();
            Instant end = Instant.now();

            if (result.isSuccess()) {
                return persistAttempt(
                        UUID.randomUUID().toString(),
                        executionId,
                        workflowId,
                        stateId,
                        actionDef,
                        workflowContext,
                        attemptNo,
                        ActionExecutionStatus.SUCCESS,
                        result,
                        start,
                        end
                );
            }

            if (!result.isRetriable()) {
                log.warn("Stopping retries for action '{}' after non-retriable failure on attempt {}: {}",
                        actionDef.getActionId(), attemptNo, result.getErrorMessage());
                return persistAttempt(
                        UUID.randomUUID().toString(),
                        executionId,
                        workflowId,
                        stateId,
                        actionDef,
                        workflowContext,
                        attemptNo,
                        ActionExecutionStatus.FAILED,
                        result,
                        start,
                        end
                );
            }

            if (offset == maxAttempts - 1) {
                log.error("Action '{}' exhausted {} attempts. Last error: {}",
                        actionDef.getActionId(), maxAttempts, result.getErrorMessage());
                return persistAttempt(
                        UUID.randomUUID().toString(),
                        executionId,
                        workflowId,
                        stateId,
                        actionDef,
                        workflowContext,
                        attemptNo,
                        ActionExecutionStatus.FAILED,
                        result,
                        start,
                        end
                );
            }

            String retryRecordId = UUID.randomUUID().toString();
            persistAttempt(
                    retryRecordId,
                    executionId,
                    workflowId,
                    stateId,
                    actionDef,
                    workflowContext,
                    attemptNo,
                    ActionExecutionStatus.RETRYING,
                    result,
                    start,
                    end
            );

            long delayMillis = retryStrategy.computeDelayMillis(actionDef.getRetryPolicy(), offset + 1);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ActionResult interruptedResult = ActionResult.nonRetriableFailure(
                        "Retry backoff interrupted after attempt " + attemptNo + ": " + exception.getMessage(),
                        result.getOutput()
                );
                return persistAttempt(
                        retryRecordId,
                        executionId,
                        workflowId,
                        stateId,
                        actionDef,
                        workflowContext,
                        attemptNo,
                        ActionExecutionStatus.FAILED,
                        interruptedResult,
                        start,
                        Instant.now()
                );
            }
        }

        throw new IllegalStateException("Action execution loop ended unexpectedly for action: " + actionDef.getActionId());
    }

    private ActionExecutionRecord persistAttempt(
            String actionExecutionId,
            String executionId,
            String workflowId,
            String stateId,
            ActionDefinition actionDef,
            WorkflowContext workflowContext,
            int attemptNo,
            ActionExecutionStatus status,
            ActionResult result,
            Instant startedAt,
            Instant finishedAt
    ) {
        ActionExecutionRecord record = new ActionExecutionRecord(
                actionExecutionId,
                executionId,
                workflowId,
                stateId,
                actionDef.getActionId(),
                attemptNo,
                status,
                workflowContext,
                result.getOutput(),
                result.getErrorMessage(),
                startedAt,
                finishedAt
        );
        return actionExecutionRepository.save(record);
    }
}
