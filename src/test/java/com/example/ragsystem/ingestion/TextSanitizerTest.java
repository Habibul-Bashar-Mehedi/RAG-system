package com.example.ragsystem.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer();

    @Test
    void removesControlCharactersAndNormalizesWhitespace() {
        String raw = "Hello\u0000   world\n\n\n\nNext\t\tline";

        String clean = sanitizer.sanitize(raw);

        assertThat(clean).doesNotContain("\u0000");
        assertThat(clean).contains("Hello world");
        assertThat(clean).contains("Next line");
        assertThat(clean).doesNotContain("\n\n\n");
    }
}
