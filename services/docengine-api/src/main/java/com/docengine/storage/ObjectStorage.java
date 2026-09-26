package com.docengine.storage;

import java.io.InputStream;

public interface ObjectStorage {
    String store(String bucket, String objectName, InputStream input, long size, String contentType);
}
