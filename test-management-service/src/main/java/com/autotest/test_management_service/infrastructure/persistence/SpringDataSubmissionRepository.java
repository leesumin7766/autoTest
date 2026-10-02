package com.autotest.test_management_service.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SpringDataSubmissionRepository extends JpaRepository<SubmissionEntity, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select submission from SubmissionEntity submission where submission.id = :id")
	java.util.Optional<SubmissionEntity> findByIdForUpdate(@Param("id") UUID id);
}