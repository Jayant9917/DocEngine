package com.docengine.entity;

import com.docengine.enums.JobStatus;
import com.docengine.enums.JobType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private String inputReference;
    private String resultReference;
    private String claimedBy;
    private LocalDateTime leaseUntil;
    private int attemptCount;
    private String errorDetails;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    protected Job() {
        // Required by JPA. Application code should use the factory method.
    }

    public Job(UUID id, UUID tenantId, JobType jobType, JobStatus status,
               String inputReference, LocalDateTime createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.jobType = jobType;
        this.status = status;
        this.inputReference = inputReference;
        this.createdAt = createdAt;
        this.attemptCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public JobType getJobType() { return jobType; }
    public JobStatus getStatus() { return status; }
    public String getInputReference() { return inputReference; }
    public String getResultReference() { return resultReference; }
    public String getClaimedBy() { return claimedBy; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorDetails() { return errorDetails; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public void setStatus(JobStatus status) { this.status = status; }
    public void setResultReference(String resultReference) { this.resultReference = resultReference; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
