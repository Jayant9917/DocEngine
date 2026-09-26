package com.docengine.validation;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidInputApiIntegrationTest {
    private static final String API_URL =
            System.getProperty("docengine.test.api-url", "http://127.0.0.1:8081");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void invalidApiKeyReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs/00000000-0000-0000-0000-000000000001"))
                .header("X-API-Key", "wrong-key")
                .GET()
                .build();

        assertEquals(401, httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void missingApiKeyReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs/00000000-0000-0000-0000-000000000001"))
                .GET()
                .build();

        assertEquals(401, httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void missingJobFieldsReturns400() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs"))
                .header("X-API-Key", "demo-tenant-key")
                .header("Idempotency-Key", "automated-invalid-input-001")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"input\":{}}"))
                .build();

        assertEquals(400, httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void nonCsvUploadReturns400() throws Exception {
        String boundary = "DocEngineBoundary";
        String multipart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"not-a-csv.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "plain text\r\n"
                + "--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/uploads"))
                .header("X-API-Key", "demo-tenant-key")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8))
                .build();

        assertEquals(400, httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
