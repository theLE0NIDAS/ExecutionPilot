package com.executionpilot.engine.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.WorkflowContext;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Service
public class AsyncActionExecutionDispatcher {

    private final ActionExecutionService actionExecutionService;
    private final ExecutorService actionExecutor;

    @Autowired
    public AsyncActionExecutionDispatcher(ActionExecutionService actionExecutionService) {
        this(
                actionExecutionService,
                Executors.newCachedThreadPool(daemonThreadFactory("executionpilot-action-executor-"))
        );
    }

    public AsyncActionExecutionDispatcher(ActionExecutionService actionExecutionService, ExecutorService actionExecutor) {
        this.actionExecutionService = actionExecutionService;
        this.actionExecutor = actionExecutor;
    }

    public CompletableFuture<List<ActionExecutionRecord>> executeActionWithHistoryAsync(
            String executionId,
            String workflowId,
            String stateId,
            ActionDefinition actionDefinition,
            WorkflowContext workflowContext,
            int startingAttemptNo
    ) {
        return CompletableFuture.supplyAsync(
                () -> actionExecutionService.executeActionWithHistory(
                        executionId,
                        workflowId,
                        stateId,
                        actionDefinition,
                        workflowContext,
                        startingAttemptNo
                ),
                actionExecutor
        );
    }

    @PreDestroy
    void shutdown() {
        actionExecutor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + thread.threadId());
            thread.setDaemon(true);
            return thread;
        };
    }
}
