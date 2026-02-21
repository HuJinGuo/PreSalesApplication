package com.bidcollab.repository;

import com.bidcollab.entity.SectionAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionAssetRepository extends JpaRepository<SectionAsset, Long> {
  List<SectionAsset> findByIndustryTagContainingAndScopeTagContainingAndKeywordsContaining(String industryTag, String scopeTag, String keywords);
  void deleteBySectionIdIn(List<Long> sectionIds);
  void deleteByVersionIdIn(List<Long> versionIds);
}
