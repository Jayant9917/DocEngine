package com.docengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.docengine.dto.JobRequest;
import com.docengine.dto.JobResponse;
import com.docengine.entity.IdempotencyKey;
import com.docengine.entity.Job;
import com.docengine.enums.JobStatus;
import com.docengine.messaging.JobMessage;
import com.docengine.repository.IdempotencyKeyRepository;
import com.docengine.repository.JobRepository;
import com.docengine.storage.ObjectStorage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectStorage objectStorage;
    private final int resultUrlExpirySeconds;
    private final Counter jobSubmissionSuccessCounter;
    private final Counter jobSubmissionFailureCounter;

    public JobService(JobRepository jobRepository, IdempotencyKeyRepository idempotencyKeyRepository,
                      RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                      ObjectStorage objectStorage,
                      MeterRegistry meterRegistry,
                      @org.springframework.beans.factory.annotation.Value("${docengine.storage.result-url-expiry-seconds:900}")
                      int resultUrlExpirySeconds) {
        this.jobRepository = jobRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.objectStorage = objectStorage;
        this.resultUrlExpirySeconds = resultUrlExpirySeconds;
        this.jobSubmissionSuccessCounter = meterRegistry.counter("docengine_job_submissions", "outcome", "success");
        this.jobSubmissionFailureCounter = meterRegistry.counter("docengine_job_submissions", "outcome", "failure");
    }

    @Transactional
    public JobResponse create(UUID tenantId, String idempotencyKey, JobRequest request) {
        try {
            JobResponse response = createInternal(tenantId, idempotencyKey, request);
            jobSubmissionSuccessCounter.increment();
            return response;
        } catch (RuntimeException exception) {
            jobSubmissionFailureCounter.increment();
            throw exception;
        }
    }

    private JobResponse createInternal(UUID tenantId, String idempotencyKey, JobRequest request) {
        if (request == null || request.jobType() == null || request.input() == null
                || request.input().dataFile() == null || request.input().dataFile().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "jobType and input.dataFile are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
        }
        String requestHash = hash(request);
        var existing = idempotencyKeyRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with a different request");
            }
            Job job = jobRepository.findById(existing.get().getJobId()).orElseThrow();
            return response(job);
        }

        UUID jobId = UUID.randomUUID();
        Job job = new Job(jobId, tenantId, request.jobType(), JobStatus.QUEUED,
                request.input().dataFile(), LocalDateTime.now());
        jobRepository.save(job);
        try {
            idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(
                    UUID.randomUUID(), tenantId, idempotencyKey, jobId, requestHash));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already exists", exception);
        }
        rabbitTemplate.convertAndSend("docengine.jobs", "job.created",
                new JobMessage(jobId, tenantId, request.jobType(), request.input().month()));
        return response(job);
    }

    @Transactional(readOnly = true)
    public JobResponse find(UUID tenantId, UUID jobId) {
        return response(jobRepository.findByIdAndTenantId(jobId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found")));
    }

    private JobResponse response(Job job) {
        if (job.getResultReference() == null || job.getResultReference().isBlank()) {
            return new JobResponse(job.getId(), job.getStatus(), null, null);
        }
        String reference = job.getResultReference();
        if (!reference.startsWith("s3://")) {
            throw new IllegalStateException("Unsupported result reference: " + reference);
        }
        String location = reference.substring("s3://".length());
        int separator = location.indexOf('/');
        if (separator <= 0 || separator == location.length() - 1) {
            throw new IllegalStateException("Invalid result reference: " + reference);
        }
        String bucket = location.substring(0, separator);
        String objectName = location.substring(separator + 1);
        Instant expiresAt = Instant.now().plusSeconds(resultUrlExpirySeconds);
        String resultUrl = objectStorage.presignedGetUrl(bucket, objectName, resultUrlExpirySeconds);
        return new JobResponse(job.getId(), job.getStatus(), resultUrl, expiresAt);
    }

    private String hash(JobRequest request) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash job request", exception);
        }
    }
}
