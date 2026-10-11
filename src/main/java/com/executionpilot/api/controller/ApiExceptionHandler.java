package com.executionpilot.api.controller;

import com.executionpilot.api.dto.ApiErrorResponse;
import com.executionpilot.api.service.WorkflowLifecycleService;
import com.executionpilot.common.exception.ConflictException;
import com.executionpilot.common.exception.InvalidWorkflowDefinitionException;
import com.executionpilot.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final WorkflowLifecycleService workflowLifecycleService;

    public ApiExceptionHandler(WorkflowLifecycleService workflowLifecycleService) {
        this.workflowLifecycleService = workflowLifecycleService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", details);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidWorkflowDefinitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidWorkflow(InvalidWorkflowDefinitionException exception) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_WORKFLOW",
                exception.getMessage(),
                workflowLifecycleService.toValidationDetails(exception.getValidationResult())
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", exception.getMessage(), List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            List<String> details
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, details, Instant.now()));
    }
}
