package com.executionpilot.engine.event;

public interface EventBus {

    <T> void subscribe(Class<T> eventType, EventHandler<? super T> handler);

    void publish(Object event);
}
