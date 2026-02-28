package com.bidcollab.repository;

import com.bidcollab.entity.DomainCategoryRelation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainCategoryRelationRepository extends JpaRepository<DomainCategoryRelation, Long> {
  List<DomainCategoryRelation> findAllByOrderBySourceCategoryRefIdAscTargetCategoryRefIdAscIdAsc();

  List<DomainCategoryRelation> findByEnabledTrueOrderBySourceCategoryRefIdAscTargetCategoryRefIdAscIdAsc();

  Optional<DomainCategoryRelation> findBySourceCategoryRefIdAndTargetCategoryRefIdAndRelationLabel(
      Long sourceCategoryId,
      Long targetCategoryId,
      String relationLabel);

  void deleteBySourceCategoryRefIdOrTargetCategoryRefId(Long sourceCategoryId, Long targetCategoryId);
}
