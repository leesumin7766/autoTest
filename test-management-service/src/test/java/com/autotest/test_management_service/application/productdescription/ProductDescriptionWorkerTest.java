package com.autotest.test_management_service.application.productdescription;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProductDescriptionWorkerTest {
    @Test
    void queuedTaskKeepsHeartbeatingAndCancelStopsItsHeartbeat() throws Exception {
        var jobs = mock(ProductDescriptionJobRepository.class);
        var gateway = mock(ProductDescriptionGateway.class);
        var worker = new ProductDescriptionWorker(jobs, gateway, mock(SubmissionPersistenceService.class),
                mock(FileStoragePort.class), new ObjectMapper(), 1, 2, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = mock(ProductDescriptionJob.class);
        var second = mock(ProductDescriptionJob.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(first.jobId()).thenReturn(firstId);
        when(first.attempt()).thenReturn(1);
        when(second.jobId()).thenReturn(secondId);
        when(second.attempt()).thenReturn(1);
        when(jobs.find(firstId)).thenAnswer(call -> { entered.countDown(); release.await(); return Optional.empty(); });
        try {
            worker.submit(first);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            worker.submit(second);
            verify(jobs, timeout(2500).atLeast(2)).touch(secondId, 1);
            verify(jobs, never()).find(secondId);
            worker.abort(secondId);
            Thread.sleep(100);
            clearInvocations(jobs);
            Thread.sleep(1100);
            verify(jobs, never()).touch(secondId, 1);
        } finally {
            release.countDown();
            worker.shutdown();
        }
    }
}
