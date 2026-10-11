package com.example.ragsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        Chunking chunking,
        Retrieval retrieval,
        VectorStore vectorStore,
        Embedding embedding,
        Llm llm,
        Qdrant qdrant,
        Upload upload,
        Retry retry
) {

    public RagProperties {
        chunking = chunking == null ? new Chunking(800, 150, 40) : chunking;
        retrieval = retrieval == null ? new Retrieval(5, 0.2d) : retrieval;
        vectorStore = vectorStore == null ? new VectorStore("qdrant", "rag_chunks") : vectorStore;
        embedding = embedding == null ? new Embedding(null, null, "text-embedding-3-small", 1536, 30, 32) : embedding;
        llm = llm == null ? new Llm(null, null, "gpt-4o-mini", 60, 0.0d) : llm;
        qdrant = qdrant == null ? new Qdrant("http://localhost:6333", 10) : qdrant;
        upload = upload == null ? new Upload("./data/uploads", 20) : upload;
        retry = retry == null ? new Retry(3, 500) : retry;
    }

    public record Chunking(Integer size, Integer overlap, Integer minChars) {
        public Chunking {
            size = size == null || size <= 0 ? 800 : size;
            overlap = overlap == null || overlap < 0 ? 150 : overlap;
            minChars = minChars == null || minChars <= 0 ? 40 : minChars;
        }
    }

    public record Retrieval(Integer topK, Double minScore) {
        public Retrieval {
            topK = topK == null || topK <= 0 ? 5 : topK;
            minScore = minScore == null ? 0.2d : minScore;
        }
    }

    public record VectorStore(String type, String collection) {
        public VectorStore {
            type = type == null || type.isBlank() ? "qdrant" : type;
            collection = collection == null || collection.isBlank() ? "rag_chunks" : collection;
        }
    }

    public record Embedding(String baseUrl, String apiKey, String model, Integer dimension, Integer timeoutSeconds, Integer maxBatch) {
        public Embedding {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
            model = model == null || model.isBlank() ? "text-embedding-3-small" : model;
            dimension = dimension == null || dimension <= 0 ? 1536 : dimension;
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
            maxBatch = maxBatch == null || maxBatch <= 0 ? 32 : maxBatch;
        }
    }

    public record Llm(String baseUrl, String apiKey, String model, Integer timeoutSeconds, Double temperature) {
        public Llm {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
            model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : timeoutSeconds;
            temperature = temperature == null ? 0.0d : temperature;
        }
    }

    public record Qdrant(String baseUrl, Integer timeoutSeconds) {
        public Qdrant {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:6333" : baseUrl;
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 10 : timeoutSeconds;
        }
    }

    public record Upload(String dir, Integer maxSizeMb) {
        public Upload {
            dir = dir == null || dir.isBlank() ? "./data/uploads" : dir;
            maxSizeMb = maxSizeMb == null || maxSizeMb <= 0 ? 20 : maxSizeMb;
        }
    }

    public record Retry(Integer maxAttempts, Integer backoffMs) {
        public Retry {
            maxAttempts = maxAttempts == null || maxAttempts <= 0 ? 3 : maxAttempts;
            backoffMs = backoffMs == null || backoffMs < 0 ? 500 : backoffMs;
        }
    }
}
