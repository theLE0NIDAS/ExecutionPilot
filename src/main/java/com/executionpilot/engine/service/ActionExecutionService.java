package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import com.executionpilot.action.executor.command.ActionCommand;
import com.executionpilot.action.executor.decorator.LoggingActionExecutorDecorator;
import com.executionpilot.action.executor.decorator.RetryActionExecutorDecorator;
import com.executionpilot.action.executor.decorator.TimeoutActionExecutorDecorator;
import com.executionpilot.action.factory.ActionExecutorFactory;
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
    private final ActionExecutionRepository actionExecutionRepository;

    public ActionExecutionService(
            ActionExecutorFactory executorFactory,
            ActionExecutionRepository actionExecutionRepository
    ) {
        this.executorFactory = executorFactory;
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
            int attemptNo
    ) {
        ActionExecutor rawExecutor = executorFactory.getExecutor(actionDef.getType());

        // Decorator chain: Logging wraps Retry wraps Timeout wraps raw executor
        ActionExecutor decorated = new LoggingActionExecutorDecorator(
                new RetryActionExecutorDecorator(
                        new TimeoutActionExecutorDecorator(rawExecutor)
                )
        );

        ActionContext context = new ActionContext(executionId, workflowId, stateId, actionDef, workflowContext);
        ActionCommand command = new ActionCommand(context, decorated);

        Instant start = Instant.now();
        ActionResult result = command.execute();
        Instant end = Instant.now();

        ActionExecutionStatus status = result.isSuccess()
                ? ActionExecutionStatus.SUCCESS
                : ActionExecutionStatus.FAILED;

        ActionExecutionRecord record = new ActionExecutionRecord(
                UUID.randomUUID().toString(),
                executionId,
                workflowId,
                stateId,
                actionDef.getActionId(),
                attemptNo,
                status,
                workflowContext,
                result.getOutput(),
                result.getErrorMessage(),
                start,
                end
        );

        return actionExecutionRepository.save(record);
    }
}
