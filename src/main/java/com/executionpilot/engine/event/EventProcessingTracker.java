package com.executionpilot.engine.event;

public interface EventProcessingTracker {

    boolean markIfUnprocessed(String eventId, String handlerId);
}
