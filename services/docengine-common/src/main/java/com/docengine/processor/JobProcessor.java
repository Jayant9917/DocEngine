package com.docengine.processor;

import com.docengine.enums.JobType;

public interface JobProcessor {

    JobType supports();

    void process(String inputReference, String resultReference);
}
