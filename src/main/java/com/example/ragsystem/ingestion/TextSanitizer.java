package com.example.ragsystem.ingestion;

import org.springframework.stereotype.Component;

@Component
public class TextSanitizer {

    public String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\t' || c == '\r') {
                builder.append(c == '\r' ? '\n' : c);
            } else if (!Character.isISOControl(c)) {
                builder.append(c);
            }
        }
        String normalized = builder.toString()
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }
}
