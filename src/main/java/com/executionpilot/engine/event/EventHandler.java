package com.executionpilot.engine.event;

public interface EventHandler<T> {

    String handlerId();

    void handle(T event);
}
