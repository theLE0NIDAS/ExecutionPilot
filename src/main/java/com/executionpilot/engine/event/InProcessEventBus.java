package com.executionpilot.engine.event;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Component
public class InProcessEventBus implements EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlersByType = new ConcurrentHashMap<>();
    private final EventProcessingTracker eventProcessingTracker;
    private final ExecutorService dispatcherExecutor;

    @Autowired
    public InProcessEventBus(EventProcessingTracker eventProcessingTracker) {
        this(
                eventProcessingTracker,
                Executors.newCachedThreadPool(daemonThreadFactory("executionpilot-event-bus-"))
        );
    }

    public InProcessEventBus(EventProcessingTracker eventProcessingTracker, ExecutorService dispatcherExecutor) {
        this.eventProcessingTracker = eventProcessingTracker;
        this.dispatcherExecutor = dispatcherExecutor;
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventHandler<? super T> handler) {
        handlersByType.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void publish(Object event) {
        List<EventHandler<?>> handlers = handlersByType.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>());
        for (EventHandler<?> handler : handlers) {
            dispatcherExecutor.submit(() -> dispatch(handler, event));
        }
    }

    @PreDestroy
    void shutdown() {
        dispatcherExecutor.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(EventHandler<?> rawHandler, Object rawEvent) {
        EventHandler<T> handler = (EventHandler<T>) rawHandler;
        T event = (T) rawEvent;

        if (rawEvent instanceof WorkflowAsyncSignal signal) {
            boolean shouldProcess = eventProcessingTracker.markIfUnprocessed(signal.getEventId(), handler.handlerId());
            if (!shouldProcess) {
                return;
            }
        }

        handler.handle(event);
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
