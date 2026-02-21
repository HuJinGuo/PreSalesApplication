package com.bidcollab.repository;

import com.bidcollab.entity.SectionReuseTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectionReuseTraceRepository extends JpaRepository<SectionReuseTrace, Long> {
  List<SectionReuseTrace> findByTargetSectionIdOrderByCreatedAtDesc(Long sectionId);

  @Modifying
  @Query("""
      delete from SectionReuseTrace t
      where t.targetSectionId in :sectionIds
         or t.sourceSectionId in :sectionIds
      """)
  int deleteBySectionIds(@Param("sectionIds") List<Long> sectionIds);

  @Modifying
  @Query("""
      delete from SectionReuseTrace t
      where t.targetVersionId in :versionIds
         or t.sourceVersionId in :versionIds
      """)
  int deleteByVersionIds(@Param("versionIds") List<Long> versionIds);
}
