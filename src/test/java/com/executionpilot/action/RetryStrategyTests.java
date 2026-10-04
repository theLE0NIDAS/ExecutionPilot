package com.executionpilot.action;

import com.executionpilot.action.definition.RetryBackoffType;
import com.executionpilot.action.definition.RetryPolicy;
import com.executionpilot.action.retry.ExponentialBackoffRetryStrategy;
import com.executionpilot.action.retry.FixedDelayRetryStrategy;
import com.executionpilot.action.retry.JitterRetryStrategy;
import com.executionpilot.action.retry.RetryStrategyFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryStrategyTests {

    @Test
    void fixedDelayStrategy_returnsInitialDelay() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(25), Duration.ofMillis(100), RetryBackoffType.FIXED_DELAY);
        FixedDelayRetryStrategy strategy = new FixedDelayRetryStrategy();

        assertThat(strategy.computeDelayMillis(policy, 1)).isEqualTo(25L);
        assertThat(strategy.computeDelayMillis(policy, 3)).isEqualTo(25L);
    }

    @Test
    void exponentialStrategy_doublesAndCapsAtMaxDelay() {
        RetryPolicy policy = new RetryPolicy(4, Duration.ofMillis(10), Duration.ofMillis(25), RetryBackoffType.EXPONENTIAL);
        ExponentialBackoffRetryStrategy strategy = new ExponentialBackoffRetryStrategy();

        assertThat(strategy.computeDelayMillis(policy, 1)).isEqualTo(10L);
        assertThat(strategy.computeDelayMillis(policy, 2)).isEqualTo(20L);
        assertThat(strategy.computeDelayMillis(policy, 3)).isEqualTo(25L);
    }

    @Test
    void jitterStrategy_returnsDelayWithinExpectedRange() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(10), Duration.ofMillis(100), RetryBackoffType.JITTER);
        JitterRetryStrategy strategy = new JitterRetryStrategy();

        long delay = strategy.computeDelayMillis(policy, 2);

        assertThat(delay).isGreaterThanOrEqualTo(20L);
        assertThat(delay).isLessThanOrEqualTo(29L);
    }

    @Test
    void factory_resolvesRegisteredStrategy() {
        RetryStrategyFactory factory = RetryStrategyFactory.defaultFactory();

        assertThat(factory.getStrategy(RetryBackoffType.FIXED_DELAY)).isInstanceOf(FixedDelayRetryStrategy.class);
        assertThat(factory.getStrategy(RetryBackoffType.EXPONENTIAL)).isInstanceOf(ExponentialBackoffRetryStrategy.class);
        assertThat(factory.getStrategy(RetryBackoffType.JITTER)).isInstanceOf(JitterRetryStrategy.class);
    }
}
