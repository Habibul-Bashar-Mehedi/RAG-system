package com.example.ragsystem.chat;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSessionRequest(
        UUID documentId,
        @Size(max = 120) String title
) {
}
