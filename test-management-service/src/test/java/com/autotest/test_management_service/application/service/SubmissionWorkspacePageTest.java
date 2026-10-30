package com.autotest.test_management_service.application.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionWorkspacePageTest {
    @Test void manualReviewPagePassesValidationAndChecksOwnership() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new SubmissionWorkspaceService(jdbc);
        // An absent owner must be checked after page validation, before any write.
        assertThrows(com.autotest.common_lib.exception.CommonException.class,
            () -> service.update(UUID.randomUUID(), 1, null, "manual-review"));
        verify(jdbc).query(anyString(), any(RowMapper.class), any(UUID.class));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
