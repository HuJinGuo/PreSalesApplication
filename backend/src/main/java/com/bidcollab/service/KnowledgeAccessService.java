package com.bidcollab.service;

import com.bidcollab.entity.KnowledgeDocument;
import com.bidcollab.entity.KnowledgeDocumentPermission;
import com.bidcollab.enums.KnowledgeVisibility;
import com.bidcollab.repository.KnowledgeDocumentPermissionRepository;
import com.bidcollab.repository.KnowledgeDocumentRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAccessService {
  private final KnowledgeDocumentRepository documentRepository;
  private final KnowledgeDocumentPermissionRepository permissionRepository;

  public KnowledgeAccessService(KnowledgeDocumentRepository documentRepository,
      KnowledgeDocumentPermissionRepository permissionRepository) {
    this.documentRepository = documentRepository;
    this.permissionRepository = permissionRepository;
  }

  public List<KnowledgeDocument> listAccessibleDocuments(Long kbId, Long userId) {
    List<KnowledgeDocument> docs = documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId);
    if (docs.isEmpty()) {
      return List.of();
    }
    if (userId == null) {
      return docs.stream().filter(doc -> doc.getVisibility() == KnowledgeVisibility.PUBLIC).toList();
    }
    Set<Long> grantedDocIds = permissionRepository.findByUserId(userId).stream()
        .map(KnowledgeDocumentPermission::getKnowledgeDocumentId)
        .collect(Collectors.toSet());
    return docs.stream()
        .filter(doc -> doc.getVisibility() == KnowledgeVisibility.PUBLIC
            || userId.equals(doc.getCreatedBy())
            || grantedDocIds.contains(doc.getId()))
        .toList();
  }

  public Set<Long> listAccessibleDocumentIds(Long kbId, Long userId) {
    return listAccessibleDocuments(kbId, userId).stream()
        .map(KnowledgeDocument::getId)
        .collect(Collectors.toSet());
  }
}

