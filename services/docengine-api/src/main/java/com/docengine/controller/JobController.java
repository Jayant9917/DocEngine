package com.docengine.controller;

import com.docengine.dto.JobRequest;
import com.docengine.dto.JobResponse;
import com.docengine.service.JobService;
import com.docengine.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final TenantService tenantService;
    private final JobService jobService;

    public JobController(TenantService tenantService, JobService jobService) {
        this.tenantService = tenantService;
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                              @RequestBody JobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(jobService.create(tenantService.resolveTenant(apiKey), idempotencyKey, request));
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                           @PathVariable UUID jobId) {
        return jobService.find(tenantService.resolveTenant(apiKey), jobId);
    }
}
