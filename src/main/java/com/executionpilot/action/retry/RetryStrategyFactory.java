package com.executionpilot.action.retry;

import com.executionpilot.action.definition.RetryBackoffType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetryStrategyFactory {

    private final List<RetryStrategy> retryStrategies;

    public RetryStrategyFactory(List<RetryStrategy> retryStrategies) {
        this.retryStrategies = List.copyOf(retryStrategies);
    }

    public RetryStrategy getStrategy(RetryBackoffType backoffType) {
        return retryStrategies.stream()
                .filter(strategy -> strategy.supports(backoffType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No RetryStrategy registered for backoffType: " + backoffType));
    }

    public static RetryStrategyFactory defaultFactory() {
        return new RetryStrategyFactory(List.of(
                new FixedDelayRetryStrategy(),
                new ExponentialBackoffRetryStrategy(),
                new JitterRetryStrategy()
        ));
    }
}
