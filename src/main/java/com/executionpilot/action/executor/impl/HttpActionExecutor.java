package com.executionpilot.action.executor.impl;

import com.executionpilot.action.definition.ActionType;
import com.executionpilot.action.executor.ActionContext;
import com.executionpilot.action.executor.ActionExecutor;
import com.executionpilot.action.executor.ActionResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Executes HTTP_CALL actions using Java's built-in HttpClient.
 * Config keys: url (required), method (default GET), body (optional), timeoutSeconds (default 10)
 */
@Component
public class HttpActionExecutor implements ActionExecutor {

    private final HttpClient httpClient;

    public HttpActionExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> config = context.getActionDefinition().getConfig();
        String url = (String) config.get("url");

        if (url == null || url.isBlank()) {
            return ActionResult.failure("HTTP action config is missing required 'url'.");
        }

        String method = config.getOrDefault("method", "GET").toString().toUpperCase();
        String body = config.containsKey("body") ? config.get("body").toString() : null;
        int timeoutSeconds = config.containsKey("timeoutSeconds")
                ? Integer.parseInt(config.get("timeoutSeconds").toString())
                : 10;

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            if ("POST".equals(method) || "PUT".equals(method)) {
                String requestBody = body != null ? body : "";
                requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(requestBody));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            Map<String, Object> output = Map.of(
                    "statusCode", response.statusCode(),
                    "body", response.body()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ActionResult.success(output);
            } else {
                return ActionResult.failure("HTTP call returned non-2xx status: " + response.statusCode(), output);
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failure("HTTP call failed: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.HTTP_CALL;
    }
}
