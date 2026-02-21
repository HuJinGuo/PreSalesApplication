package com.bidcollab.repository;

import com.bidcollab.entity.DomainDictionaryEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainDictionaryEntryRepository extends JpaRepository<DomainDictionaryEntry, Long> {
  List<DomainDictionaryEntry> findByPackIdOrderByCategoryAscTermAsc(Long packId);

  List<DomainDictionaryEntry> findByPackIdInAndEnabledTrue(Collection<Long> packIds);

  Optional<DomainDictionaryEntry> findByPackIdAndCategoryAndTerm(Long packId, String category, String term);

  void deleteByPackId(Long packId);
}
