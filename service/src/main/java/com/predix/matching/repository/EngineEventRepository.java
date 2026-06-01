package com.predix.matching.repository;

import com.predix.matching.domain.entity.EngineEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineEventRepository extends JpaRepository<EngineEventEntity, Long> {
}
