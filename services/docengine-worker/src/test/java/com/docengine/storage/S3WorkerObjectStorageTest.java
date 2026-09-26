package com.docengine.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3WorkerObjectStorageTest {

    @Test
    void downloadsInputAndStoresGeneratedResultUnderConfiguredPrefixes() {
        S3Client client = mock(S3Client.class);
        S3WorkerObjectStorage storage = new S3WorkerObjectStorage(client, "inputs", "outputs");
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> downloaded = mock(ResponseInputStream.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(downloaded);
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertSame(downloaded, storage.downloadInput("private-bucket", "inputs/jobs/1/input.csv"));
        String reference = storage.storeResult("private-bucket", "jobs/1/report.pdf",
                new ByteArrayInputStream(new byte[]{4, 5}), 2, "application/pdf");

        assertEquals("s3://private-bucket/outputs/jobs/1/report.pdf", reference);
        verify(client).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(request ->
                request.key().equals("inputs/jobs/1/input.csv")));
        verify(client).putObject(org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                        request.bucket().equals("private-bucket")
                                && request.key().equals("outputs/jobs/1/report.pdf")
                                && request.contentType().equals("application/pdf")),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }
}
