package com.executionpilot.api.service;

import com.executionpilot.action.definition.ActionDefinition;
import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.api.dto.ActionDefinitionRequest;
import com.executionpilot.api.dto.ActionDefinitionResponse;
import com.executionpilot.api.dto.ActionExecutionRecordResponse;
import com.executionpilot.api.dto.CreateWorkflowRequest;
import com.executionpilot.api.dto.ExecutionStatusResponse;
import com.executionpilot.api.dto.GenerateWorkflowRequest;
import com.executionpilot.api.dto.RetryPolicyRequest;
import com.executionpilot.api.dto.RetryPolicyResponse;
import com.executionpilot.api.dto.StateDefinitionRequest;
import com.executionpilot.api.dto.StateDefinitionResponse;
import com.executionpilot.api.dto.TransitionDefinitionRequest;
import com.executionpilot.api.dto.TransitionDefinitionResponse;
import com.executionpilot.api.dto.WorkflowPreviewResponse;
import com.executionpilot.api.dto.WorkflowResponse;
import com.executionpilot.api.dto.WorkflowVersionSummaryResponse;
import com.executionpilot.api.dto.TriggerWorkflowExecutionRequest;
import com.executionpilot.common.exception.ConflictException;
import com.executionpilot.common.exception.InvalidWorkflowDefinitionException;
import com.executionpilot.common.exception.ResourceNotFoundException;
import com.executionpilot.engine.execution.ActionExecutionRecord;
import com.executionpilot.engine.execution.WorkflowExecution;
import com.executionpilot.engine.service.AsyncWorkflowExecutionService;
import com.executionpilot.persistence.repository.ActionExecutionRepository;
import com.executionpilot.persistence.repository.WorkflowDefinitionRepository;
import com.executionpilot.persistence.repository.WorkflowExecutionRepository;
import com.executionpilot.workflow.builder.ActionDefinitionBuilder;
import com.executionpilot.workflow.builder.RetryPolicyBuilder;
import com.executionpilot.workflow.builder.StateDefinitionBuilder;
import com.executionpilot.workflow.builder.TransitionDefinitionBuilder;
import com.executionpilot.workflow.builder.WorkflowBuilder;
import com.executionpilot.workflow.domain.StateDefinition;
import com.executionpilot.workflow.domain.StateType;
import com.executionpilot.workflow.domain.TransitionDefinition;
import com.executionpilot.workflow.domain.Workflow;
import com.executionpilot.workflow.domain.WorkflowStatus;
import com.executionpilot.workflow.generator.GeneratedWorkflowDraft;
import com.executionpilot.workflow.generator.WorkflowGenerationMode;
import com.executionpilot.workflow.generator.WorkflowGenerator;
import com.executionpilot.workflow.generator.WorkflowGeneratorFactory;
import com.executionpilot.workflow.validator.ValidationErrorCode;
import com.executionpilot.workflow.validator.ValidationMessage;
import com.executionpilot.workflow.validator.WorkflowValidationResult;
import com.executionpilot.workflow.validator.WorkflowValidatorChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WorkflowLifecycleService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ActionExecutionRepository actionExecutionRepository;
    private final AsyncWorkflowExecutionService asyncWorkflowExecutionService;
    private final WorkflowValidatorChain workflowValidatorChain = WorkflowValidatorChain.defaultChain();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowLifecycleService(
            WorkflowDefinitionRepository workflowDefinitionRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            ActionExecutionRepository actionExecutionRepository,
            AsyncWorkflowExecutionService asyncWorkflowExecutionService
    ) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.actionExecutionRepository = actionExecutionRepository;
        this.asyncWorkflowExecutionService = asyncWorkflowExecutionService;
    }

    public WorkflowResponse createWorkflowDraft(CreateWorkflowRequest request) {
        boolean exists = workflowDefinitionRepository.findByWorkflowIdOrderByVersionDesc(request.workflowId()).stream()
                .anyMatch(workflow -> workflow.getVersion() == request.version());
        if (exists) {
            throw new ConflictException("Workflow version already exists for workflowId='"
                    + request.workflowId() + "' and version=" + request.version());
        }

        Workflow saved = workflowDefinitionRepository.save(toWorkflow(request, WorkflowStatus.DRAFT));
        return toWorkflowResponse(saved);
    }

    public WorkflowResponse generateWorkflowDraft(GenerateWorkflowRequest request) {
        WorkflowGenerationMode generationMode = WorkflowGenerationMode.from(request.generationStrategy());
        WorkflowGenerator generator = WorkflowGeneratorFactory.get(generationMode);
        GeneratedWorkflowDraft generated = generator.generate(request.prompt(), request.workflowName());

        WorkflowValidationResult validationResult = workflowValidatorChain.validate(toWorkflow(generated.request(), WorkflowStatus.DRAFT));
        if (!validationResult.isValid()) {
            throw new InvalidWorkflowDefinitionException("Generated workflow failed validation.", validationResult);
        }

        return createWorkflowDraft(generated.request());
    }

    public WorkflowResponse updateWorkflowDraft(String workflowId, int version, CreateWorkflowRequest request) {
        requireWorkflowPathMatchesBody(workflowId, version, request);
        workflowDefinitionRepository.findByWorkflowIdAndVersionAndStatus(workflowId, version, WorkflowStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft workflow not found for workflowId='" + workflowId + "' and version=" + version));

        Workflow saved = workflowDefinitionRepository.save(toWorkflow(request, WorkflowStatus.DRAFT));
        return toWorkflowResponse(saved);
    }

    public WorkflowResponse activateWorkflow(String workflowId, int version) {
        Workflow draft = workflowDefinitionRepository.findByWorkflowIdAndVersionAndStatus(workflowId, version, WorkflowStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft workflow not found for workflowId='" + workflowId + "' and version=" + version));

        Optional<Workflow> active = workflowDefinitionRepository.findByWorkflowIdAndVersionAndStatus(workflowId, version, WorkflowStatus.ACTIVE);
        if (active.isPresent()) {
            throw new ConflictException("Workflow is already active for workflowId='" + workflowId + "' and version=" + version);
        }

        WorkflowValidationResult validationResult = workflowValidatorChain.validate(draft);
        if (!validationResult.isValid()) {
            throw new InvalidWorkflowDefinitionException("Workflow definition failed validation.", validationResult);
        }

        Workflow activatedWorkflow = new Workflow(
                draft.getWorkflowId(),
                draft.getName(),
                draft.getVersion(),
                WorkflowStatus.ACTIVE,
                draft.getStartStateId(),
                draft.getEndStateId(),
                draft.getStates(),
                draft.getTransitions()
        );
        Workflow saved = workflowDefinitionRepository.save(activatedWorkflow);
        return toWorkflowResponse(saved);
    }

    public WorkflowPreviewResponse previewWorkflow(CreateWorkflowRequest request) {
        Workflow draft = toWorkflow(request, WorkflowStatus.DRAFT);
        WorkflowValidationResult validationResult = workflowValidatorChain.validate(draft);

        List<String> topologicalOrder = validationResult.getTopologicalOrder() == null
                ? List.of()
                : validationResult.getTopologicalOrder();

        return new WorkflowPreviewResponse(
                validationResult.isValid(),
                validationResult.isValid() ? "Workflow is ready for activation." : "Workflow contains validation errors.",
                validationResult.getMessages().stream().map(ValidationMessage::message).toList(),
                topologicalOrder
        );
    }

    public String exportWorkflow(String workflowId, int version) {
        Workflow workflow = workflowDefinitionRepository.findByWorkflowIdAndVersion(workflowId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workflow not found for workflowId='" + workflowId + "' and version=" + version));
        return toJson(toExportDocument(workflow));
    }

    public WorkflowResponse importWorkflow(String json) {
        try {
            WorkflowImportDocument imported = parseImportDocument(json);
            return createWorkflowDraft(new CreateWorkflowRequest(
                    imported.workflowId(),
                    imported.name(),
                    imported.version(),
                    imported.startStateId(),
                    imported.endStateId(),
                    imported.states().stream().map(this::toStateRequest).toList(),
                    imported.transitions().stream().map(this::toTransitionRequest).toList()
            ));
        } catch (RuntimeException ex) {
            WorkflowValidationResult invalid = WorkflowValidationResult.builder()
                    .addMessage(new ValidationMessage(ValidationErrorCode.TOPOLOGICAL_SORT_FAILED,
                            "Imported workflow is invalid or not supported.", true))
                    .build();
            throw new InvalidWorkflowDefinitionException("Imported workflow is invalid or not supported.", invalid);
        }
    }

    public WorkflowResponse getWorkflow(String workflowId, int version, WorkflowStatus status) {
        Workflow workflow;
        if (status != null) {
            workflow = workflowDefinitionRepository.findByWorkflowIdAndVersionAndStatus(workflowId, version, status)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Workflow not found for workflowId='" + workflowId + "', version=" + version + ", status=" + status));
        } else {
            workflow = findWorkflowByPreference(workflowId, version)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Workflow not found for workflowId='" + workflowId + "' and version=" + version));
        }
        return toWorkflowResponse(workflow);
    }

    public List<WorkflowVersionSummaryResponse> listWorkflowVersions(String workflowId) {
        List<Workflow> workflows = workflowDefinitionRepository.findByWorkflowIdOrderByVersionDesc(workflowId);
        if (workflows.isEmpty()) {
            throw new ResourceNotFoundException("No workflow versions found for workflowId='" + workflowId + "'.");
        }

        return workflows.stream()
                .sorted(Comparator.comparing(Workflow::getVersion).reversed().thenComparing(workflow -> workflow.getStatus().name()))
                .map(workflow -> new WorkflowVersionSummaryResponse(
                        workflow.getWorkflowId(),
                        workflow.getName(),
                        workflow.getVersion(),
                        workflow.getStatus()
                ))
                .toList();
    }

    public ExecutionStatusResponse triggerExecution(TriggerWorkflowExecutionRequest request) {
        String executionId = asyncWorkflowExecutionService.startWorkflowAsync(
                request.workflowId(),
                request.workflowVersion(),
                request.context()
        );
        WorkflowExecution execution = workflowExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution was not persisted after async trigger."));
        return toExecutionStatusResponse(execution);
    }

    public ExecutionStatusResponse getExecutionStatus(String executionId) {
        WorkflowExecution execution = workflowExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found for executionId='" + executionId + "'."));
        return toExecutionStatusResponse(execution);
    }

    public List<ActionExecutionRecordResponse> getActionHistory(String executionId, String stateId) {
        workflowExecutionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found for executionId='" + executionId + "'."));

        List<ActionExecutionRecord> records = stateId == null || stateId.isBlank()
                ? actionExecutionRepository.findByExecutionIdOrderByStartedAtAsc(executionId)
                : actionExecutionRepository.findByExecutionIdAndStateIdOrderByAttemptNoAsc(executionId, stateId);

        return records.stream()
                .map(this::toActionExecutionRecordResponse)
                .toList();
    }

    public List<String> toValidationDetails(WorkflowValidationResult validationResult) {
        return validationResult.getMessages().stream()
                .map(this::toValidationDetail)
                .toList();
    }

    private StateDefinitionRequest toStateRequest(StateImport state) {
        return new StateDefinitionRequest(
                state.stateId(),
                state.name(),
                state.type(),
                state.actions().stream().map(this::toActionRequest).toList()
        );
    }

    private ActionDefinitionRequest toActionRequest(ActionImport action) {
        return new ActionDefinitionRequest(
                action.actionId(),
                action.name(),
                action.type(),
                action.config(),
                toRetryPolicyRequest(action.retryPolicy())
        );
    }

    private RetryPolicyRequest toRetryPolicyRequest(RetryPolicyImport retryPolicy) {
        return new RetryPolicyRequest(
                retryPolicy.maxAttempts(),
                retryPolicy.initialDelayMillis(),
                retryPolicy.maxDelayMillis(),
                retryPolicy.backoffType()
        );
    }

    private TransitionDefinitionRequest toTransitionRequest(TransitionImport transition) {
        return new TransitionDefinitionRequest(
                transition.transitionId(),
                transition.fromStateId(),
                transition.toStateId(),
                transition.eventName(),
                transition.guardExpression()
        );
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize workflow payload.", ex);
        }
    }

    private WorkflowImportDocument parseImportDocument(String json) {
        try {
            return objectMapper.readValue(json, WorkflowImportDocument.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Workflow import payload is malformed.", ex);
        }
    }

    private WorkflowImportDocument toExportDocument(Workflow workflow) {
        return new WorkflowImportDocument(
                workflow.getWorkflowId(),
                workflow.getName(),
                workflow.getVersion(),
                workflow.getStatus(),
                workflow.getStartStateId(),
                workflow.getEndStateId(),
                workflow.getStates().values().stream()
                        .sorted(Comparator.comparing(StateDefinition::getStateId))
                        .map(this::toStateImport)
                        .toList(),
                workflow.getTransitions().stream().map(this::toTransitionImport).toList()
        );
    }

    private StateImport toStateImport(StateDefinition state) {
        return new StateImport(
                state.getStateId(),
                state.getName(),
                state.getType(),
                state.getActions().stream().map(this::toActionImport).toList()
        );
    }

    private ActionImport toActionImport(ActionDefinition action) {
        return new ActionImport(
                action.getActionId(),
                action.getName(),
                action.getType(),
                action.getConfig(),
                toRetryPolicyImport(action.getRetryPolicy())
        );
    }

    private RetryPolicyImport toRetryPolicyImport(RetryPolicy policy) {
        return new RetryPolicyImport(
                policy.getMaxAttempts(),
                policy.getInitialDelay().toMillis(),
                policy.getMaxDelay().toMillis(),
                policy.getBackoffType()
        );
    }

    private TransitionImport toTransitionImport(TransitionDefinition transition) {
        return new TransitionImport(
                transition.getTransitionId(),
                transition.getFromStateId(),
                transition.getToStateId(),
                transition.getEventName(),
                transition.getGuardExpression()
        );
    }

    private String toValidationDetail(ValidationMessage message) {
        return message.code().name() + ": " + message.message();
    }

    private void requireWorkflowPathMatchesBody(String workflowId, int version, CreateWorkflowRequest request) {
        if (!workflowId.equals(request.workflowId())) {
            throw new IllegalArgumentException("workflowId in path must match workflowId in request body.");
        }
        if (version != request.version()) {
            throw new IllegalArgumentException("version in path must match version in request body.");
        }
    }

    private Optional<Workflow> findWorkflowByPreference(String workflowId, int version) {
        for (WorkflowStatus candidateStatus : List.of(WorkflowStatus.ACTIVE, WorkflowStatus.DRAFT, WorkflowStatus.DEPRECATED)) {
            Optional<Workflow> workflow = workflowDefinitionRepository.findByWorkflowIdAndVersionAndStatus(workflowId, version, candidateStatus);
            if (workflow.isPresent()) {
                return workflow;
            }
        }
        return Optional.empty();
    }

    private Workflow toWorkflow(CreateWorkflowRequest request, WorkflowStatus status) {
        WorkflowBuilder builder = new WorkflowBuilder()
                .workflowId(request.workflowId())
                .name(request.name())
                .version(request.version())
                .status(status)
                .startStateId(request.startStateId())
                .endStateId(request.endStateId());

        for (StateDefinitionRequest stateRequest : request.states()) {
            builder.addState(toStateDefinition(stateRequest));
        }
        for (var transitionRequest : request.transitions()) {
            builder.addTransition(new TransitionDefinitionBuilder()
                    .transitionId(transitionRequest.transitionId())
                    .fromStateId(transitionRequest.fromStateId())
                    .toStateId(transitionRequest.toStateId())
                    .eventName(transitionRequest.eventName())
                    .guardExpression(transitionRequest.guardExpression())
                    .build());
        }
        return builder.build();
    }

    private StateDefinition toStateDefinition(StateDefinitionRequest request) {
        StateDefinitionBuilder builder = new StateDefinitionBuilder()
                .stateId(request.stateId())
                .name(request.name())
                .type(request.type());
        for (ActionDefinitionRequest actionRequest : request.actions()) {
            builder.addAction(toActionDefinition(actionRequest));
        }
        return builder.build();
    }

    private ActionDefinition toActionDefinition(ActionDefinitionRequest request) {
        return new ActionDefinitionBuilder()
                .actionId(request.actionId())
                .name(request.name())
                .type(request.type())
                .config(request.config())
                .retryPolicy(toRetryPolicy(request.retryPolicy()))
                .build();
    }

    private RetryPolicy toRetryPolicy(RetryPolicyRequest request) {
        return new RetryPolicyBuilder()
                .maxAttempts(request.maxAttempts())
                .initialDelay(Duration.ofMillis(request.initialDelayMillis()))
                .maxDelay(Duration.ofMillis(request.maxDelayMillis()))
                .backoffType(request.backoffType())
                .build();
    }

    private WorkflowResponse toWorkflowResponse(Workflow workflow) {
        List<StateDefinitionResponse> states = workflow.getStates().values().stream()
                .sorted(Comparator.comparing(StateDefinition::getStateId))
                .map(state -> new StateDefinitionResponse(
                        state.getStateId(),
                        state.getName(),
                        state.getType(),
                        state.getActions().stream()
                                .map(action -> new ActionDefinitionResponse(
                                        action.getActionId(),
                                        action.getName(),
                                        action.getType(),
                                        action.getConfig(),
                                        new RetryPolicyResponse(
                                                action.getRetryPolicy().getMaxAttempts(),
                                                action.getRetryPolicy().getInitialDelay().toMillis(),
                                                action.getRetryPolicy().getMaxDelay().toMillis(),
                                                action.getRetryPolicy().getBackoffType()
                                        )
                                ))
                                .toList()
                ))
                .toList();

        List<TransitionDefinitionResponse> transitions = workflow.getTransitions().stream()
                .map(transition -> new TransitionDefinitionResponse(
                        transition.getTransitionId(),
                        transition.getFromStateId(),
                        transition.getToStateId(),
                        transition.getEventName(),
                        transition.getGuardExpression()
                ))
                .toList();

        return new WorkflowResponse(
                workflow.getWorkflowId(),
                workflow.getName(),
                workflow.getVersion(),
                workflow.getStatus(),
                workflow.getStartStateId(),
                workflow.getEndStateId(),
                states,
                transitions
        );
    }

    private ExecutionStatusResponse toExecutionStatusResponse(WorkflowExecution execution) {
        return new ExecutionStatusResponse(
                execution.getExecutionId(),
                execution.getWorkflowId(),
                execution.getWorkflowVersion(),
                execution.getStatus(),
                execution.getCurrentStateId(),
                execution.getContext().getData(),
                execution.getStartedAt(),
                execution.getUpdatedAt()
        );
    }

    private ActionExecutionRecordResponse toActionExecutionRecordResponse(ActionExecutionRecord record) {
        return new ActionExecutionRecordResponse(
                record.getActionExecutionId(),
                record.getExecutionId(),
                record.getWorkflowId(),
                record.getStateId(),
                record.getActionId(),
                record.getAttemptNo(),
                record.getStatus(),
                record.getInput(),
                record.getOutput(),
                record.getErrorMessage(),
                record.getStartedAt(),
                record.getFinishedAt()
        );
    }

    private record WorkflowImportDocument(
            String workflowId,
            String name,
            int version,
            WorkflowStatus status,
            String startStateId,
            String endStateId,
            List<StateImport> states,
            List<TransitionImport> transitions
    ) {
    }

    private record StateImport(
            String stateId,
            String name,
            StateType type,
            List<ActionImport> actions
    ) {
    }

    private record ActionImport(
            String actionId,
            String name,
            ActionType type,
            java.util.Map<String, Object> config,
            RetryPolicyImport retryPolicy
    ) {
    }

    private record RetryPolicyImport(
            int maxAttempts,
            long initialDelayMillis,
            long maxDelayMillis,
            RetryBackoffType backoffType
    ) {
    }

    private record TransitionImport(
            String transitionId,
            String fromStateId,
            String toStateId,
            String eventName,
            String guardExpression
    ) {
    }
}
