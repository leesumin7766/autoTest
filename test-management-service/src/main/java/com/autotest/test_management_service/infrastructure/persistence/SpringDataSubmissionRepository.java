package com.autotest.test_management_service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataSubmissionRepository extends JpaRepository<SubmissionEntity, UUID> {
}