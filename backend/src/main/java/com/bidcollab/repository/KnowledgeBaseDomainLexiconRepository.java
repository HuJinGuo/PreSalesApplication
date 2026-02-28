package com.bidcollab.repository;

import com.bidcollab.entity.KnowledgeBaseDomainLexicon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseDomainLexiconRepository extends JpaRepository<KnowledgeBaseDomainLexicon, Long> {
  List<KnowledgeBaseDomainLexicon> findByKnowledgeBaseIdOrderByCategoryAscTermAsc(Long knowledgeBaseId);
  List<KnowledgeBaseDomainLexicon> findByKnowledgeBaseIdAndEnabledTrue(Long knowledgeBaseId);
  Optional<KnowledgeBaseDomainLexicon> findByKnowledgeBaseIdAndCategoryRefIdAndTerm(
      Long knowledgeBaseId, Long categoryId, String term);
  boolean existsByCategoryRefId(Long categoryId);
  boolean existsByCategoryIgnoreCase(String category);
  void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
