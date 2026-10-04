package com.executionpilot.action.executor;

import java.util.Objects;

/**
 * Output contract returned by every ActionExecutor.
 * Carries success/failure status, output payload, and error message.
 */
public final class ActionResult {

    public enum Status {
        SUCCESS,
        FAILURE
    }

    private final Status status;
    private final Object output;
    private final String errorMessage;

    private ActionResult(Status status, Object output, String errorMessage) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.output = output;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ActionResult success(Object output) {
        return new ActionResult(Status.SUCCESS, output, null);
    }

    public static ActionResult failure(String errorMessage) {
        return new ActionResult(Status.FAILURE, null, errorMessage == null ? "Unknown error" : errorMessage);
    }

    public static ActionResult failure(String errorMessage, Object partialOutput) {
        return new ActionResult(Status.FAILURE, partialOutput, errorMessage == null ? "Unknown error" : errorMessage);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public Object getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
