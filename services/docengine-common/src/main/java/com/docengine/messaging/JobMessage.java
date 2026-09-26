package com.docengine.messaging;

import com.docengine.enums.JobType;

import java.util.UUID;

public record JobMessage(UUID jobId, UUID tenantId, JobType jobType, String month) {
}
