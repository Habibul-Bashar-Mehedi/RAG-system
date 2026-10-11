package com.example.ragsystem.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatQuestionRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 2000, message = "question must be at most 2000 characters")
        String question
) {
}
