package com.docengine.dto;

import com.docengine.enums.JobType;

public record JobRequest(JobType jobType, JobInput input) {

    public record JobInput(String month, String dataFile) {
    }
}
