package com.bidcollab.repository;

import com.bidcollab.entity.DomainDictionaryPack;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainDictionaryPackRepository extends JpaRepository<DomainDictionaryPack, Long> {
  Optional<DomainDictionaryPack> findByCode(String code);
}
