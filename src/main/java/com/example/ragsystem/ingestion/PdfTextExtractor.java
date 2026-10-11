package com.example.ragsystem.ingestion;

import com.example.ragsystem.common.RagException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor {

    private final TextSanitizer sanitizer;

    public PdfTextExtractor(TextSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public List<PageText> extract(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>(pageCount);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = sanitizer.sanitize(stripper.getText(document));
                pages.add(new PageText(page, text));
            }
            return pages;
        } catch (IOException ex) {
            throw new RagException("Failed to read PDF: " + ex.getMessage(), ex);
        }
    }

    public int pageCount(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            throw new RagException("Failed to read PDF: " + ex.getMessage(), ex);
        }
    }
}
