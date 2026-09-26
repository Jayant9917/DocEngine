package com.docengine.listener;

import com.docengine.config.WorkerRabbitConfig;
import com.docengine.entity.Job;
import com.docengine.enums.JobStatus;
import com.docengine.messaging.JobMessage;
import com.docengine.processor.CsvToPdfProcessor;
import com.docengine.repository.WorkerJobRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class JobListener {
    private final WorkerJobRepository jobRepository;
    private final CsvToPdfProcessor processor;
    private final boolean crashAfterClaim;
    private final int leaseSeconds;
    private final int testProcessingDelaySeconds;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    private final Counter receivedCounter;
    private final Counter claimedCounter;
    private final Counter claimConflictCounter;
    private final Counter completedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public JobListener(WorkerJobRepository jobRepository, CsvToPdfProcessor processor,
                       @Value("${docengine.test.crash-after-claim:false}") boolean crashAfterClaim,
                       @Value("${docengine.worker.lease-seconds:300}") int leaseSeconds,
                       @Value("${docengine.test.processing-delay-seconds:0}") int testProcessingDelaySeconds,
                       MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.processor = processor;
        this.crashAfterClaim = crashAfterClaim;
        this.leaseSeconds = leaseSeconds;
        this.testProcessingDelaySeconds = testProcessingDelaySeconds;
        this.receivedCounter = meterRegistry.counter("docengine_jobs_received");
        this.claimedCounter = meterRegistry.counter("docengine_jobs_claimed");
        this.claimConflictCounter = meterRegistry.counter("docengine_jobs_claim_conflicts");
        this.completedCounter = meterRegistry.counter("docengine_jobs_completed");
        this.failedCounter = meterRegistry.counter("docengine_jobs_failed");
        this.processingTimer = meterRegistry.timer("docengine_job_processing");
    }

    @RabbitListener(queues = WorkerRabbitConfig.QUEUE)
    public void receive(JobMessage message) {
        receivedCounter.increment();
        String workerId = "worker-" + UUID.randomUUID();
        if (jobRepository.claim(message.jobId(), workerId, leaseSeconds) == 0) {
            claimConflictCounter.increment();
            return;
        }
        claimedCounter.increment();
        Timer.Sample processingSample = Timer.start();
        if (crashAfterClaim) {
            Runtime.getRuntime().halt(1);
        }

        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> jobRepository.renewLease(message.jobId(), workerId, leaseSeconds),
                Math.max(1, leaseSeconds / 3L),
                Math.max(1, leaseSeconds / 3L),
                TimeUnit.SECONDS);
        try {
            if (testProcessingDelaySeconds > 0) {
                try {
                    Thread.sleep(testProcessingDelaySeconds * 1000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Worker processing was interrupted", exception);
                }
            }
            Job job = jobRepository.findById(message.jobId()).orElseThrow();
            String resultReference = processor.process(
                    job.getInputReference(), message.month(), job.getTenantId().toString(), job.getId().toString());
            job.setResultReference(resultReference);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            completedCounter.increment();
        } catch (RuntimeException exception) {
            failedCounter.increment();
            heartbeat.cancel(false);
            Job job = jobRepository.findById(message.jobId()).orElseThrow();
            job.setStatus(JobStatus.FAILED);
            job.setErrorDetails(exception.getMessage());
            jobRepository.save(job);
            throw exception;
        } finally {
            heartbeat.cancel(false);
            processingSample.stop(processingTimer);
        }
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }
}
