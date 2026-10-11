package com.executionpilot.api.controller;

import com.executionpilot.api.dto.ActionExecutionRecordResponse;
import com.executionpilot.api.dto.CreateWorkflowRequest;
import com.executionpilot.api.dto.ExecutionStatusResponse;
import com.executionpilot.api.dto.TriggerWorkflowExecutionRequest;
import com.executionpilot.api.dto.WorkflowResponse;
import com.executionpilot.api.dto.WorkflowVersionSummaryResponse;
import com.executionpilot.api.service.WorkflowLifecycleService;
import com.executionpilot.workflow.domain.WorkflowStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowLifecycleService workflowLifecycleService;

    public WorkflowController(WorkflowLifecycleService workflowLifecycleService) {
        this.workflowLifecycleService = workflowLifecycleService;
    }

    @PostMapping("/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowLifecycleService.createWorkflowDraft(request);
    }

    @PutMapping("/workflows/{workflowId}/versions/{version}/draft")
    public WorkflowResponse updateWorkflowDraft(
            @PathVariable String workflowId,
            @PathVariable int version,
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        return workflowLifecycleService.updateWorkflowDraft(workflowId, version, request);
    }

    @PostMapping("/workflows/{workflowId}/versions/{version}/activate")
    public WorkflowResponse activateWorkflow(@PathVariable String workflowId, @PathVariable int version) {
        return workflowLifecycleService.activateWorkflow(workflowId, version);
    }

    @GetMapping("/workflows/{workflowId}/versions/{version}")
    public WorkflowResponse getWorkflow(
            @PathVariable String workflowId,
            @PathVariable int version,
            @RequestParam(required = false) WorkflowStatus status
    ) {
        return workflowLifecycleService.getWorkflow(workflowId, version, status);
    }

    @GetMapping("/workflows/{workflowId}/versions")
    public List<WorkflowVersionSummaryResponse> listWorkflowVersions(@PathVariable String workflowId) {
        return workflowLifecycleService.listWorkflowVersions(workflowId);
    }

    @PostMapping("/executions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionStatusResponse triggerExecution(@Valid @RequestBody TriggerWorkflowExecutionRequest request) {
        return workflowLifecycleService.triggerExecution(request);
    }

    @GetMapping("/executions/{executionId}")
    public ExecutionStatusResponse getExecutionStatus(@PathVariable String executionId) {
        return workflowLifecycleService.getExecutionStatus(executionId);
    }

    @GetMapping("/executions/{executionId}/actions")
    public List<ActionExecutionRecordResponse> getActionHistory(
            @PathVariable String executionId,
            @RequestParam(required = false) String stateId
    ) {
        return workflowLifecycleService.getActionHistory(executionId, stateId);
    }
}
