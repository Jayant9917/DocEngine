package com.docengine.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class WorkerObjectStorage {
    private final MinioClient client;

    public WorkerObjectStorage(
            @Value("${docengine.storage.endpoint}") String endpoint,
            @Value("${docengine.storage.access-key}") String accessKey,
            @Value("${docengine.storage.secret-key}") String secretKey) {
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
    }

    public InputStream download(String bucket, String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not download input object", exception);
        }
    }

    public String upload(String bucket, String objectName, InputStream input,
                         long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, size, -1)
                    .contentType(contentType)
                    .build());
            return "s3://" + bucket + "/" + objectName;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not upload result object", exception);
        }
    }
}
