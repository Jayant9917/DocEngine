package com.docengine.storage;

import java.io.InputStream;

/** Provider-neutral object storage operations used by the API and worker. */
public interface ObjectStorage {
    String storeInput(String bucket, String objectName, InputStream input, long size, String contentType);

    InputStream downloadInput(String bucket, String objectName);

    String storeResult(String bucket, String objectName, InputStream input, long size, String contentType);

    String presignedGetUrl(String bucket, String objectName, int expirySeconds);
}
