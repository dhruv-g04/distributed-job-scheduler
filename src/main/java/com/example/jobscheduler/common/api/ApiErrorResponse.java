package com.example.jobscheduler.common.api;

import java.util.Map;

public record ApiErrorResponse(String code, String message, String path, Map<String, String> details) {

    public ApiErrorResponse {
        details = Map.copyOf(details);
    }
}