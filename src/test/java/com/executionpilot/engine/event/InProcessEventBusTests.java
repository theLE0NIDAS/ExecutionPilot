package com.executionpilot.engine.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InProcessEventBusTests {

    @Test
    void processesSameSignalOnlyOncePerHandler() throws InterruptedException {
        InProcessEventBus eventBus = new InProcessEventBus(new InMemoryEventProcessingTracker());
        AtomicInteger invocationCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.subscribe(WorkflowAsyncSignal.class, new EventHandler<>() {
            @Override
            public String handlerId() {
                return "test-handler";
            }

            @Override
            public void handle(WorkflowAsyncSignal event) {
                invocationCount.incrementAndGet();
                latch.countDown();
            }
        });

        WorkflowAsyncSignal signal = new WorkflowAsyncSignal(
                "evt-1",
                "exec-1",
                "wf-1",
                1,
                "start",
                AsyncWorkflowSignalType.STATE_STARTED,
                "state started",
                Map.of(),
                Instant.now()
        );

        eventBus.publish(signal);
        eventBus.publish(signal);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(Duration.ofMillis(100));
        assertThat(invocationCount.get()).isEqualTo(1);
    }
}
