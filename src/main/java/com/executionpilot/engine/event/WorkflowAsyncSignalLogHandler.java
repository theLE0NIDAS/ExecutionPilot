package com.executionpilot.engine.event;

import com.executionpilot.engine.event.WorkflowEventLogEntry;
import com.executionpilot.persistence.repository.WorkflowEventLogRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class WorkflowAsyncSignalLogHandler implements EventHandler<WorkflowAsyncSignal> {

    private static final Set<AsyncWorkflowSignalType> LOGGABLE_SIGNAL_TYPES = Set.of(
            AsyncWorkflowSignalType.STATE_STARTED,
            AsyncWorkflowSignalType.STATE_COMPLETED,
            AsyncWorkflowSignalType.ACTION_SUCCEEDED,
            AsyncWorkflowSignalType.ACTION_FAILED,
            AsyncWorkflowSignalType.RETRY_SCHEDULED,
            AsyncWorkflowSignalType.WORKFLOW_COMPLETED,
            AsyncWorkflowSignalType.WORKFLOW_FAILED
    );

    private final WorkflowEventLogRepository workflowEventLogRepository;

    public WorkflowAsyncSignalLogHandler(EventBus eventBus, WorkflowEventLogRepository workflowEventLogRepository) {
        this.workflowEventLogRepository = workflowEventLogRepository;
        eventBus.subscribe(WorkflowAsyncSignal.class, this);
    }

    @Override
    public String handlerId() {
        return "workflow-async-signal-log-handler";
    }

    @Override
    public void handle(WorkflowAsyncSignal signal) {
        if (!LOGGABLE_SIGNAL_TYPES.contains(signal.getSignalType())) {
            return;
        }

        workflowEventLogRepository.save(new WorkflowEventLogEntry(
                UUID.randomUUID().toString(),
                signal.getExecutionId(),
                signal.getWorkflowId(),
                signal.getStateId(),
                signal.getSignalType().name(),
                signal.getMessage(),
                signal.getPayload(),
                signal.getOccurredAt()
        ));
    }
}
