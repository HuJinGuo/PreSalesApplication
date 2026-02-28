package com.bidcollab.repository;

import com.bidcollab.entity.DomainCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainCategoryRepository extends JpaRepository<DomainCategory, Long> {
  List<DomainCategory> findByStatusOrderBySortOrderAscIdAsc(String status);

  List<DomainCategory> findAllByOrderBySortOrderAscIdAsc();

  Optional<DomainCategory> findByCode(String code);
}
