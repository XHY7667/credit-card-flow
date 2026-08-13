package com.hx.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingIntegrationTest {

    private static final AtomicReference<String> RECEIVED_PATH = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @Value("${local.server.port}")
    private int gatewayPort;

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "CREDITCARDFLOW_SERVICE_BASE_URL",
                () -> "http://localhost:" + DOWNSTREAM.getAddress().getPort()
        );
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void routesUnchangedPathAndAuthorizationHeaderToDownstream() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + gatewayPort + "/api/v1/cards/CARD-500"))
                        .header("Authorization", "Bearer test-token")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(RECEIVED_PATH.get()).isEqualTo("/api/v1/cards/CARD-500");
        assertThat(RECEIVED_AUTHORIZATION.get()).isEqualTo("Bearer test-token");
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("downstream-response");
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", ApiGatewayRoutingIntegrationTest::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start test downstream server", exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        RECEIVED_PATH.set(exchange.getRequestURI().getPath());
        RECEIVED_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));

        byte[] body = "downstream-response".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(202, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
