package com.docengine.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ObjectStorageTest {

    @Test
    void storesInputUnderInputPrefixAndReturnsReference() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, mock(S3Presigner.class), "inputs/", "outputs/");
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String reference = storage.storeInput("private-bucket", "tenant/upload/report.csv",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "text/csv");

        assertEquals("s3://private-bucket/inputs/tenant/upload/report.csv", reference);
        verify(client).putObject(org.mockito.ArgumentMatchers.<PutObjectRequest>argThat(request ->
                        request.bucket().equals("private-bucket")
                                && request.key().equals("inputs/tenant/upload/report.csv")
                                && request.contentType().equals("text/csv")),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void downloadsAlreadyPrefixedInputWithoutAddingPrefixTwice() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, mock(S3Presigner.class), "inputs", "outputs");
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> response = mock(ResponseInputStream.class);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        assertSame(response, storage.downloadInput("private-bucket", "inputs/tenant/file.csv"));
        verify(client).getObject(org.mockito.ArgumentMatchers.<GetObjectRequest>argThat(request ->
                request.bucket().equals("private-bucket") && request.key().equals("inputs/tenant/file.csv")));
    }

    @Test
    void createsExpiringPresignedUrlForOutputPrefix() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        S3ObjectStorage storage = new S3ObjectStorage(mock(S3Client.class), presigner, "inputs", "outputs");
        PresignedGetObjectRequest signedRequest = mock(PresignedGetObjectRequest.class);
        URL expectedUrl = URI.create("https://private-bucket.s3.ap-south-1.amazonaws.com/outputs/report.pdf?signature=x").toURL();
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signedRequest);
        when(signedRequest.url()).thenReturn(expectedUrl);

        assertEquals(expectedUrl.toExternalForm(), storage.presignedGetUrl("private-bucket", "outputs/report.pdf", 900));
        verify(presigner).presignGetObject(org.mockito.ArgumentMatchers.<GetObjectPresignRequest>argThat(request ->
                request.signatureDuration().getSeconds() == 900
                        && request.getObjectRequest().bucket().equals("private-bucket")
                        && request.getObjectRequest().key().equals("outputs/report.pdf")));
    }

    @Test
    void wrapsMissingObjectAndStorageFailuresAndRejectsNonPositiveExpiry() {
        S3Client client = mock(S3Client.class);
        S3ObjectStorage storage = new S3ObjectStorage(client, mock(S3Presigner.class), "inputs", "outputs");
        when(client.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404).message("missing").build());
        doThrow(S3Exception.builder().statusCode(403).message("denied").build())
                .when(client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));

        assertThrows(IllegalStateException.class,
                () -> storage.downloadInput("private-bucket", "inputs/missing.csv"));
        assertThrows(IllegalStateException.class,
                () -> storage.storeResult("private-bucket", "report.pdf",
                        new ByteArrayInputStream(new byte[]{1}), 1, "application/pdf"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.presignedGetUrl("private-bucket", "outputs/report.pdf", 0));
    }
}
