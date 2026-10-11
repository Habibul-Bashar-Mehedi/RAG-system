package com.example.ragsystem.ingestion;

import com.example.ragsystem.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final RagProperties properties;

    public TextChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<Chunk> chunk(List<PageText> pages) {
        int size = properties.chunking().size();
        int overlap = Math.min(properties.chunking().overlap(), size - 1);
        int minChars = properties.chunking().minChars();
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (PageText page : pages) {
            String text = page.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + size, text.length());
                String piece = text.substring(start, end).trim();
                if (piece.length() >= minChars) {
                    chunks.add(new Chunk(index++, page.pageNumber(), piece));
                }
                if (end >= text.length()) {
                    break;
                }
                start = Math.max(end - overlap, start + 1);
            }
        }
        return chunks;
    }
}
