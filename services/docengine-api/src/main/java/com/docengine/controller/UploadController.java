package com.docengine.controller;

import com.docengine.service.TenantService;
import com.docengine.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final TenantService tenantService;
    private final ObjectStorage storage;
    private final String inputBucket;
    private final Counter uploadSuccessCounter;
    private final Counter uploadFailureCounter;
    private final Timer uploadTimer;

    public UploadController(TenantService tenantService, ObjectStorage storage,
                            @Value("${docengine.storage.input-bucket}") String inputBucket,
                            MeterRegistry meterRegistry) {
        this.tenantService = tenantService;
        this.storage = storage;
        this.inputBucket = inputBucket;
        this.uploadSuccessCounter = meterRegistry.counter("docengine_uploads", "outcome", "success");
        this.uploadFailureCounter = meterRegistry.counter("docengine_uploads", "outcome", "failure");
        this.uploadTimer = meterRegistry.timer("docengine_upload_duration");
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> upload(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                      @RequestPart("file") MultipartFile file) {
        try {
            UUID tenantId = tenantService.resolveTenant(apiKey);
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }
            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CSV files are supported");
            }
            String uploadId = UUID.randomUUID().toString();
            String objectName = tenantId + "/" + uploadId + "/" + file.getOriginalFilename();
            Timer.Sample uploadSample = Timer.start();
            String reference;
            try {
                reference = storage.store(inputBucket, objectName, file.getInputStream(),
                        file.getSize(), file.getContentType() == null ? "text/csv" : file.getContentType());
            } finally {
                uploadSample.stop(uploadTimer);
            }
            uploadSuccessCounter.increment();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("uploadId", uploadId, "inputReference", reference,
                            "fileName", file.getOriginalFilename()));
        } catch (ResponseStatusException exception) {
            uploadFailureCounter.increment();
            throw exception;
        } catch (Exception exception) {
            uploadFailureCounter.increment();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed", exception);
        }
    }
}
