package com.bidcollab.repository;

import com.bidcollab.entity.AiCallTrace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AiCallTraceRepository extends JpaRepository<AiCallTrace, Long>, JpaSpecificationExecutor<AiCallTrace> {
}
