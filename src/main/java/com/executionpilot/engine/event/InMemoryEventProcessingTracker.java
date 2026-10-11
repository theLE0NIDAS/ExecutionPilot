package com.executionpilot.engine.event;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventProcessingTracker implements EventProcessingTracker {

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markIfUnprocessed(String eventId, String handlerId) {
        return processedKeys.add(eventId + "::" + handlerId);
    }
}
