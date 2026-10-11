package com.example.ragsystem.embedding;

import java.util.List;

public interface EmbeddingClient {

    int dimension();

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).getFirst();
    }
}
