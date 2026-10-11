package com.example.ragsystem.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final String responseBody;
    private final int status;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile String lastRequestBody;

    public StubHttpServer(int status, String responseBody) throws IOException {
        this.status = status;
        this.responseBody = responseBody;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public int requestCount() {
        return requestCount.get();
    }

    public String lastRequestBody() {
        return lastRequestBody;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
