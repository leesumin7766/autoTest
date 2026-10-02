package com.autotest.test_management_service.domain.submission;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.autotest.test_management_service.domain.event.SubmissionUploadedEvent;
import com.autotest.test_management_service.domain.vo.MemberId;

class SubmissionTest {
    private static final ProductId PRODUCT_ID = new ProductId(1L);
    private static final MemberId MEMBER_ID = new MemberId(2L);

    @Test
    void createsSubmissionWithSetIdentityAndInitialStatus() {
        Submission submission = Submission.create(MEMBER_ID, PRODUCT_ID);

        assertEquals(MEMBER_ID, submission.memberId());
        assertEquals(PRODUCT_ID, submission.productId());
        assertEquals(SubmissionStatus.UPLOADED, submission.status());
        assertTrue(submission.testCases().isEmpty());
    }

    @Test
    void draftUploadTransitionRegistersEvent() {
        Submission draft = SubmissionFactory.draft(MEMBER_ID, PRODUCT_ID);

        Submission uploaded = draft.markAsUploaded();

        assertEquals(SubmissionStatus.DRAFT, draft.status());
        assertEquals(SubmissionStatus.UPLOADED, uploaded.status());
        assertTrue(uploaded.uploadedAt().isPresent());
        assertEquals(1, uploaded.domainEvents().size());
        assertInstanceOf(SubmissionUploadedEvent.class, uploaded.domainEvents().getFirst());
    }

    @Test
    void processingStatusCanBeReconstitutedAndChangedWithoutFileMetadata() {
        Submission submission = Submission.reconstitute(
                SubmissionId.generate(), MEMBER_ID, PRODUCT_ID, SubmissionStatus.UPLOADED, Instant.now());

        assertEquals(SubmissionStatus.PARSED, submission.withProcessingStatus(SubmissionStatus.PARSED).status());
        assertEquals(SubmissionStatus.FAILED, submission.withProcessingStatus(SubmissionStatus.FAILED).status());
        assertThrows(IllegalArgumentException.class,
                () -> submission.withProcessingStatus(SubmissionStatus.DRAFT));
    }
}
