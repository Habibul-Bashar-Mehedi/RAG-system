package com.example.ragsystem.llm;

public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);
}
