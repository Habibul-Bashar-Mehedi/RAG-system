package com.example.ragsystem.ingestion;

import com.example.ragsystem.support.TestPdfFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor(new TextSanitizer());

    @Test
    void extractsTextPerPage() throws IOException {
        byte[] pdf = TestPdfFactory.multiPagePdf(3, "Hello world");
        Path path = Files.createTempFile("rag-test", ".pdf");
        Files.write(path, pdf);

        List<PageText> pages = extractor.extract(path);

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).pageNumber()).isEqualTo(1);
        assertThat(pages.get(2).pageNumber()).isEqualTo(3);
        assertThat(pages.get(1).text()).contains("Hello world page 2");

        Files.deleteIfExists(path);
    }
}
