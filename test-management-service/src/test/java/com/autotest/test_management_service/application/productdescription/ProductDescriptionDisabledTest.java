package com.autotest.test_management_service.application.productdescription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.application.storage.FileStoragePort;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProductDescriptionDisabledTest {
    @Test void blocksStartAndRerenderBeforeSchedulingAnything() {
        var worker = mock(ProductDescriptionWorker.class);
        var jobs = mock(ProductDescriptionJobRepository.class);
        var service = new ProductDescriptionService(mock(SubmissionRepository.class),
                mock(SubmissionPersistenceService.class), jobs, worker, mock(FileStoragePort.class),
                new ObjectMapper(), mock(PlatformTransactionManager.class), 30);
        UUID id = UUID.randomUUID();
        assertEquals("PRODUCT_DESCRIPTION_DISABLED",
                assertThrows(ProductDescriptionException.class, () -> service.start(id, 1)).code());
        assertEquals("PRODUCT_DESCRIPTION_DISABLED",
                assertThrows(ProductDescriptionException.class, () -> service.rerender(id, UUID.randomUUID(), 1)).code());
        verifyNoInteractions(worker, jobs);
    }
}
