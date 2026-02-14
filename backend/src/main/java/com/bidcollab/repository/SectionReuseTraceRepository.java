package com.bidcollab.repository;

import com.bidcollab.entity.SectionReuseTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionReuseTraceRepository extends JpaRepository<SectionReuseTrace, Long> {
  List<SectionReuseTrace> findByTargetSectionIdOrderByCreatedAtDesc(Long sectionId);
}
