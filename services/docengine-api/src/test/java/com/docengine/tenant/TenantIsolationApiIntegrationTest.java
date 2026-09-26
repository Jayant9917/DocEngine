package com.docengine.tenant;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationApiIntegrationTest {
    private static final String API_URL =
            System.getProperty("docengine.test.api-url", "http://127.0.0.1:8081");
    private static final String INPUT_REFERENCE =
            "s3://docengine-inputs/00000000-0000-0000-0000-000000000001/"
                    + "479cb083-65d1-42c2-9df7-62f5bb30918b/september-sales.csv";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void tenantCannotReadAnotherTenantsJob() throws Exception {
        String body = "{\"jobType\":\"GENERATE_MONTHLY_REPORT\","
                + "\"input\":{\"month\":\"2026-09\","
                + "\"dataFile\":\"" + INPUT_REFERENCE + "\"}}";
        HttpRequest createJob = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs"))
                .header("X-API-Key", "demo-tenant-key")
                .header("Idempotency-Key", "tenant-isolation-" + UUID.randomUUID())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> created = httpClient.send(
                createJob, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, created.statusCode());
        String jobId = jobId(created.body());

        HttpRequest readAsTenantB = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/api/v1/jobs/" + jobId))
                .header("X-API-Key", "tenant-b-key")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                readAsTenantB, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Not Found"));
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
