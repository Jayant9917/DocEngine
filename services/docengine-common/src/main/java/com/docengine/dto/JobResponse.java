package com.docengine.dto;

import com.docengine.enums.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID jobId,
        JobStatus status,
        String resultUrl,
        Instant resultUrlExpiresAt
) {
}
