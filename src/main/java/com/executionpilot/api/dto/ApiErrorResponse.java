package com.executionpilot.api.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<String> details,
        Instant timestamp
) {
    public ApiErrorResponse {
        details = details == null ? List.of() : List.copyOf(new ArrayList<>(details));
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
