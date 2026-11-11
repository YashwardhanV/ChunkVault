package com.chunkvault.repository;

import com.chunkvault.domain.GcRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GcRunRepository extends JpaRepository<GcRun, UUID> {
    Optional<GcRun> findTopByOrderByCompletedAtDesc();
}
