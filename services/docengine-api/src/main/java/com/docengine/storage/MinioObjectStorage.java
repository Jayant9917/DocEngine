package com.docengine.storage;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioObjectStorage implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);
    private final MinioClient client;
    private final MinioClient presignClient;
    private final String inputBucket;
    private final String resultBucket;

    public MinioObjectStorage(
            @Value("${docengine.storage.endpoint}") String endpoint,
            @Value("${docengine.storage.public-endpoint:${docengine.storage.endpoint}}") String publicEndpoint,
            @Value("${docengine.storage.access-key}") String accessKey,
            @Value("${docengine.storage.secret-key}") String secretKey,
            @Value("${docengine.storage.input-bucket}") String inputBucket,
            @Value("${docengine.storage.result-bucket}") String resultBucket) {
        this.inputBucket = inputBucket;
        this.resultBucket = resultBucket;
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build();
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
        this.presignClient = MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .httpClient(httpClient)
                .build();
    }

    @PostConstruct
    void ensureBuckets() {
        ensureBucket(inputBucket);
        ensureBucket(resultBucket);
    }

    private void ensureBucket(String bucket) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            } else {
                log.info("MinIO bucket {} already exists", bucket);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize MinIO bucket " + bucket, exception);
        }
    }

    @Override
    public String store(String bucket, String objectName, InputStream input,
                        long size, String contentType) {
        try {
            log.info("Uploading {} to MinIO bucket {}", objectName, bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, size, -1)
                    .contentType(contentType)
                    .build());
            return "s3://" + bucket + "/" + objectName;
        } catch (Exception exception) {
            log.error("MinIO store failed for bucket={}, object={}", bucket, objectName, exception);
            throw new IllegalStateException("Could not store object in MinIO", exception);
        }
    }

    public String presignedGetUrl(String bucket, String objectName, int expirySeconds) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception exception) {
            log.error("Could not create MinIO download URL for bucket={}, object={}", bucket, objectName, exception);
            throw new IllegalStateException("Could not create result download URL", exception);
        }
    }
}
