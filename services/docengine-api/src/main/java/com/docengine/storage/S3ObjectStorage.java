package com.docengine.storage;

import com.docengine.storage.ObjectStorage;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "docengine.storage", name = "provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String inputPrefix;
    private final String outputPrefix;

    @Autowired
    public S3ObjectStorage(
            @Value("${docengine.storage.s3.region}") String region,
            @Value("${docengine.storage.s3.input-prefix:inputs}") String inputPrefix,
            @Value("${docengine.storage.s3.output-prefix:outputs}") String outputPrefix) {
        this(S3Client.builder().region(Region.of(region)).build(),
                S3Presigner.builder().region(Region.of(region)).build(), inputPrefix, outputPrefix);
    }

    S3ObjectStorage(S3Client client, S3Presigner presigner, String inputPrefix, String outputPrefix) {
        this.client = client;
        this.presigner = presigner;
        this.inputPrefix = normalizePrefix(inputPrefix);
        this.outputPrefix = normalizePrefix(outputPrefix);
    }

    @Override
    public String storeInput(String bucket, String objectName, InputStream input, long size, String contentType) {
        return put(bucket, withPrefix(inputPrefix, objectName), input, size, contentType);
    }

    @Override
    public InputStream downloadInput(String bucket, String objectName) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(withPrefix(inputPrefix, objectName))
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not download input object from S3", exception);
        }
    }

    @Override
    public String storeResult(String bucket, String objectName, InputStream input, long size, String contentType) {
        return put(bucket, withPrefix(outputPrefix, objectName), input, size, contentType);
    }

    private String put(String bucket, String key, InputStream input, long size, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(input, size));
            return "s3://" + bucket + "/" + key;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not store object in S3", exception);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectName, int expirySeconds) {
        if (expirySeconds <= 0) {
            throw new IllegalArgumentException("Presigned URL expiry must be positive");
        }
        try {
            var request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirySeconds))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(withPrefix(outputPrefix, objectName))
                            .build())
                    .build();
            return presigner.presignGetObject(request).url().toExternalForm();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create S3 result download URL", exception);
        }
    }

    private String withPrefix(String prefix, String objectName) {
        return prefix.isEmpty() || objectName.equals(prefix) || objectName.startsWith(prefix + "/")
                ? objectName
                : prefix + "/" + objectName;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String normalized = prefix.trim().replaceAll("^/+|/+$", "");
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("S3 object prefixes must not contain '..'");
        }
        return normalized;
    }

    @PreDestroy
    void close() {
        presigner.close();
        client.close();
    }
}
