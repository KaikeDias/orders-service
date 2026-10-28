package com.dias.orders_service.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;

public class CatalogStub implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    public final AtomicInteger calls = new AtomicInteger();
    public volatile String lastMethod;
    public volatile String lastPath;

    public CatalogStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastMethod = exchange.getRequestMethod();
            lastPath = exchange.getRequestURI().getPath();
            Reply reply = replies.getOrDefault(lastPath, new Reply(404, "{}", 0, "application/json", 0));
            try (exchange) {
                try {
                    Thread.sleep(reply.delay());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", reply.contentType());
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                try {
                    Thread.sleep(reply.bodyDelay());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // A timeout test deliberately closes its connection before the delayed response.
            }
        });
        server.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public void reply(UUID id, int status, String body) { reply(id, status, body, 0, "application/json"); }
    public void reply(UUID id, int status, String body, long delay, String contentType) {
        replies.put("/products/" + id, new Reply(status, body, delay, contentType, 0));
    }
    public void product(UUID id, String price, String currency, boolean active) {
        reply(id, 200, "{\"productId\":\"" + id + "\",\"name\":\"Café\",\"price\":" + price
                + ",\"currency\":\"" + currency + "\",\"active\":" + active + "}");
    }
    public void slowBody(UUID id, long delay) {
        replies.put("/products/" + id, new Reply(200, "{}", 0, "application/json", delay));
    }
    public void reset() { replies.clear(); calls.set(0); }
    @Override public void close() { server.stop(0); }
    private record Reply(int status, String body, long delay, String contentType, long bodyDelay) {}
}
