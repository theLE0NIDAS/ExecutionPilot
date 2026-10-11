package com.executionpilot.engine.service;

import com.executionpilot.engine.event.AsyncWorkflowSignalType;
import com.executionpilot.engine.event.EventBus;
import com.executionpilot.engine.event.WorkflowAsyncSignal;
import com.executionpilot.engine.execution.ExecutionStatus;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.state.WorkflowStateMachine;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AsyncWorkflowExecutionService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowStateMachine workflowStateMachine;
    private final EventBus eventBus;

    public AsyncWorkflowExecutionService(
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowStateMachine workflowStateMachine,
            EventBus eventBus
    ) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowStateMachine = workflowStateMachine;
        this.eventBus = eventBus;
    }

    public String startWorkflowAsync(String workflowId, int workflowVersion, Map<String, Object> initialContextData) {
        Workflow workflow = workflowDefinitionRepository
                .findByWorkflowIdAndVersionAndStatus(workflowId, workflowVersion, WorkflowStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active workflow definition not found for workflowId='" + workflowId
                                + "' and version=" + workflowVersion
                ));

        String executionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        WorkflowExecution initialized = workflowStateMachine.initializeExecution(workflow, executionId, initialContextData, now);
        WorkflowExecution queued = new WorkflowExecution(
                initialized.getExecutionId(),
                initialized.getWorkflowId(),
                initialized.getWorkflowVersion(),
                ExecutionStatus.WAITING,
                initialized.getCurrentStateId(),
                initialized.getContext(),
                initialized.getStartedAt(),
                initialized.getUpdatedAt()
        );
        workflowExecutionRepository.save(queued);

        eventBus.publish(WorkflowAsyncSignal.create(
                queued.getExecutionId(),
                queued.getWorkflowId(),
                queued.getWorkflowVersion(),
                queued.getCurrentStateId(),
                AsyncWorkflowSignalType.STATE_EXECUTION_REQUESTED,
                "Scheduled asynchronous state execution for '" + queued.getCurrentStateId() + "'.",
                Map.of("phase", "async-runtime")
        ));

        return executionId;
    }
}
