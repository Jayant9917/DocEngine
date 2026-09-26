package com.docengine.idempotency;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyApiIntegrationTest {
    private static final String API_URL =
            System.getProperty("docengine.test.api-url", "http://127.0.0.1:8081");
    private static final String INPUT_REFERENCE =
            "s3://docengine-inputs/00000000-0000-0000-0000-000000000001/"
                    + "479cb083-65d1-42c2-9df7-62f5bb30918b/september-sales.csv";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void sameKeyAndSamePayloadReturnTheSameJob() throws Exception {
        String key = "automated-idempotency-" + UUID.randomUUID();
        String body = requestBody("2026-09");

        HttpResponse<String> first = submit(key, body);
        HttpResponse<String> second = submit(key, body);

        assertEquals(202, first.statusCode());
        assertEquals(202, second.statusCode());
        assertEquals(jobId(first.body()), jobId(second.body()));
    }

    @Test
    void sameKeyAndDifferentPayloadReturnConflict() throws Exception {
        String key = "automated-idempotency-" + UUID.randomUUID();

        HttpResponse<String> first = submit(key, requestBody("2026-09"));
        HttpResponse<String> second = submit(key, requestBody("2026-08"));

        assertEquals(202, first.statusCode());
        assertEquals(409, second.statusCode());
        assertTrue(second.body().contains("Conflict"));
    }

    private HttpResponse<String> submit(String idempotencyKey, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs"))
                .header("X-API-Key", "demo-tenant-key")
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String requestBody(String month) {
        return "{\"jobType\":\"GENERATE_MONTHLY_REPORT\","
                + "\"input\":{\"month\":\"" + month + "\","
                + "\"dataFile\":\"" + INPUT_REFERENCE + "\"}}";
    }

    private String jobId(String responseBody) {
        String marker = "\"jobId\":\"";
        int start = responseBody.indexOf(marker);
        assertTrue(start >= 0, "Response does not contain jobId: " + responseBody);
        start += marker.length();
        int end = responseBody.indexOf('"', start);
        return responseBody.substring(start, end);
    }
}
