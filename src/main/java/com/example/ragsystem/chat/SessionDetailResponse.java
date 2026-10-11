package com.example.ragsystem.chat;

import java.util.List;

public record SessionDetailResponse(
        SessionSummaryResponse session,
        List<MessageResponse> messages
) {
}
