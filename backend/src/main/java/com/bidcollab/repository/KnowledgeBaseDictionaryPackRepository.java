package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeBaseDictionaryPack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseDictionaryPackRepository extends JpaRepository<KnowledgeBaseDictionaryPack, Long> {
  List<KnowledgeBaseDictionaryPack> findByKnowledgeBaseIdOrderByPriorityDescIdAsc(Long knowledgeBaseId);

  Optional<KnowledgeBaseDictionaryPack> findByKnowledgeBaseIdAndPackId(Long knowledgeBaseId, Long packId);

  void deleteByKnowledgeBaseIdAndPackId(Long knowledgeBaseId, Long packId);

  void deleteByKnowledgeBaseId(Long knowledgeBaseId);

  void deleteByPackId(Long packId);
}
