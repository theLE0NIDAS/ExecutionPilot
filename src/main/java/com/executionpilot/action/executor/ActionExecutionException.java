package com.executionpilot.action.executor;

public class ActionExecutionException extends RuntimeException {

    private final boolean retriable;

    private ActionExecutionException(String message, boolean retriable, Throwable cause) {
        super(message, cause);
        this.retriable = retriable;
    }

    public static ActionExecutionException retriable(String message) {
        return new ActionExecutionException(message, true, null);
    }

    public static ActionExecutionException retriable(String message, Throwable cause) {
        return new ActionExecutionException(message, true, cause);
    }

    public static ActionExecutionException nonRetriable(String message) {
        return new ActionExecutionException(message, false, null);
    }

    public static ActionExecutionException nonRetriable(String message, Throwable cause) {
        return new ActionExecutionException(message, false, cause);
    }

    public boolean isRetriable() {
        return retriable;
    }
}
