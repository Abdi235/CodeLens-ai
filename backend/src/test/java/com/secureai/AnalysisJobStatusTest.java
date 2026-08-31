package com.secureai;

import com.secureai.model.AnalysisJob;
import com.secureai.model.AnalysisJobStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisJobStatusTest {

    @Test
    void validTransitions() {
        AnalysisJob job = AnalysisJob.builder().status(AnalysisJobStatus.QUEUED).build();
        assertTrue(job.canTransitionTo(AnalysisJobStatus.PROCESSING));
        assertTrue(job.canTransitionTo(AnalysisJobStatus.FAILED));
        assertFalse(job.canTransitionTo(AnalysisJobStatus.COMPLETED));
    }

    @Test
    void processingToCompletedOrFailed() {
        AnalysisJob job = AnalysisJob.builder().status(AnalysisJobStatus.PROCESSING).build();
        assertTrue(job.canTransitionTo(AnalysisJobStatus.COMPLETED));
        assertTrue(job.canTransitionTo(AnalysisJobStatus.FAILED));
        assertFalse(job.canTransitionTo(AnalysisJobStatus.QUEUED));
    }

    @Test
    void terminalStatesRejectTransitions() {
        AnalysisJob completed = AnalysisJob.builder().status(AnalysisJobStatus.COMPLETED).build();
        assertFalse(completed.canTransitionTo(AnalysisJobStatus.PROCESSING));

        AnalysisJob failed = AnalysisJob.builder().status(AnalysisJobStatus.FAILED).build();
        assertFalse(failed.canTransitionTo(AnalysisJobStatus.QUEUED));
    }
}
