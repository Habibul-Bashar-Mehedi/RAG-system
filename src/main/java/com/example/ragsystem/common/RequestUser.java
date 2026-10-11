package com.example.ragsystem.common;

import org.springframework.stereotype.Component;

@Component
public class RequestUser {

    public static final String HEADER = "X-User-Id";
    private static final int MAX_LENGTH = 64;

    public String resolve(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return "anonymous";
        }
        String cleaned = headerValue.trim().replaceAll("[^A-Za-z0-9_.@-]", "");
        if (cleaned.isBlank()) {
            return "anonymous";
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
