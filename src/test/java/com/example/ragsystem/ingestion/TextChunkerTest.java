package com.example.ragsystem.ingestion;

import com.example.ragsystem.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private TextChunker chunker(int size, int overlap, int minChars) {
        return new TextChunker(new RagProperties(
                new RagProperties.Chunking(size, overlap, minChars),
                null, null, null, null, null, null, null));
    }

    @Test
    void preservesPageNumbersAndProducesOverlap() {
        List<PageText> pages = List.of(
                new PageText(1, "A".repeat(100)),
                new PageText(2, "B".repeat(100)));

        List<Chunk> chunks = chunker(60, 20, 10).chunk(pages);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().filter(c -> c.pageNumber() == 1)).isNotEmpty();
        assertThat(chunks.stream().filter(c -> c.pageNumber() == 2)).isNotEmpty();
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }

    @Test
    void skipsBlankPagesAndShortChunks() {
        List<PageText> pages = List.of(
                new PageText(1, "   "),
                new PageText(2, "tiny"));

        List<Chunk> chunks = chunker(100, 10, 50).chunk(pages);

        assertThat(chunks).isEmpty();
    }
}
