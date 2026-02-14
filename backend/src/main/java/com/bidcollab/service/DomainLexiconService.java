package com.bidcollab.service;

import com.bidcollab.dto.DomainLexiconUpsertRequest;
import com.bidcollab.dto.DomainLexiconUpdateRequest;
import com.bidcollab.dto.KnowledgeLexiconResponse;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeBaseDomainLexicon;
import com.bidcollab.repository.KnowledgeBaseDomainLexiconRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainLexiconService {
  private final KnowledgeBaseDomainLexiconRepository lexiconRepository;
  private final KnowledgeBaseRepository baseRepository;
  private final CurrentUserService currentUserService;

  public DomainLexiconService(KnowledgeBaseDomainLexiconRepository lexiconRepository,
      KnowledgeBaseRepository baseRepository,
      CurrentUserService currentUserService) {
    this.lexiconRepository = lexiconRepository;
    this.baseRepository = baseRepository;
    this.currentUserService = currentUserService;
  }

  public List<KnowledgeLexiconResponse> list(Long knowledgeBaseId) {
    baseRepository.findById(knowledgeBaseId).orElseThrow(EntityNotFoundException::new);
    return lexiconRepository.findByKnowledgeBaseIdOrderByCategoryAscTermAsc(knowledgeBaseId).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Transactional
  public KnowledgeLexiconResponse upsert(DomainLexiconUpsertRequest request) {
    KnowledgeBase kb = baseRepository.findById(request.getKnowledgeBaseId()).orElseThrow(EntityNotFoundException::new);
    String category = request.getCategory().trim().toUpperCase();
    String term = request.getTerm().trim();
    KnowledgeBaseDomainLexicon lexicon = lexiconRepository
        .findByKnowledgeBaseIdAndCategoryAndTerm(kb.getId(), category, term)
        .orElseGet(() -> KnowledgeBaseDomainLexicon.builder()
            .knowledgeBase(kb)
            .category(category)
            .term(term)
            .enabled(Boolean.TRUE)
            .createdBy(currentUserService.getCurrentUserId())
            .build());
    if (request.getEnabled() != null) {
      lexicon.setEnabled(request.getEnabled());
    }
    lexiconRepository.save(lexicon);
    return toResponse(lexicon);
  }

  @Transactional
  public void delete(Long lexiconId) {
    KnowledgeBaseDomainLexicon lexicon = lexiconRepository.findById(lexiconId).orElseThrow(EntityNotFoundException::new);
    lexiconRepository.delete(lexicon);
  }

  @Transactional
  public KnowledgeLexiconResponse update(Long lexiconId, DomainLexiconUpdateRequest request) {
    KnowledgeBaseDomainLexicon lexicon = lexiconRepository.findById(lexiconId).orElseThrow(EntityNotFoundException::new);
    if (!lexicon.getKnowledgeBase().getId().equals(request.getKnowledgeBaseId())) {
      throw new IllegalArgumentException("Lexicon does not belong to knowledge base");
    }
    String category = request.getCategory().trim().toUpperCase();
    String term = request.getTerm().trim();
    lexiconRepository.findByKnowledgeBaseIdAndCategoryAndTerm(request.getKnowledgeBaseId(), category, term)
        .ifPresent(existing -> {
          if (!existing.getId().equals(lexiconId)) {
            throw new IllegalArgumentException("Lexicon term already exists in this category");
          }
        });
    lexicon.setCategory(category);
    lexicon.setTerm(term);
    if (request.getEnabled() != null) {
      lexicon.setEnabled(request.getEnabled());
    }
    lexiconRepository.save(lexicon);
    return toResponse(lexicon);
  }

  private KnowledgeLexiconResponse toResponse(KnowledgeBaseDomainLexicon lexicon) {
    return KnowledgeLexiconResponse.builder()
        .id(lexicon.getId())
        .knowledgeBaseId(lexicon.getKnowledgeBase().getId())
        .category(lexicon.getCategory())
        .term(lexicon.getTerm())
        .enabled(lexicon.getEnabled())
        .createdAt(lexicon.getCreatedAt())
        .build();
  }
}
