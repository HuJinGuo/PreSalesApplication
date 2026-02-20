package com.bidcollab.service;

import com.bidcollab.dto.SectionCreateRequest;
import com.bidcollab.dto.SectionMoveRequest;
import com.bidcollab.dto.SectionTreeNode;
import com.bidcollab.dto.SectionUpdateRequest;
import com.bidcollab.dto.SectionVersionCreateRequest;
import com.bidcollab.dto.SectionVersionResponse;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.enums.SectionStatus;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionService {
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final DocumentRepository documentRepository;
  private final CurrentUserService currentUserService;

  public SectionService(SectionRepository sectionRepository,
                        SectionVersionRepository sectionVersionRepository,
                        DocumentRepository documentRepository,
                        CurrentUserService currentUserService) {
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.documentRepository = documentRepository;
    this.currentUserService = currentUserService;
  }

  public List<SectionTreeNode> getTree(Long documentId) {
    List<Section> sections = sectionRepository.findByDocumentIdOrderBySortIndexAsc(documentId);
    Map<Long, SectionTreeNode> map = new HashMap<>();
    List<SectionTreeNode> roots = new ArrayList<>();
    for (Section section : sections) {
      SectionTreeNode node = toNode(section);
      map.put(section.getId(), node);
    }
    for (Section section : sections) {
      SectionTreeNode node = map.get(section.getId());
      if (section.getParent() == null) {
        roots.add(node);
      } else {
        SectionTreeNode parent = map.get(section.getParent().getId());
        if (parent != null) {
          parent.getChildren().add(node);
        }
      }
    }
    roots.forEach(this::sortChildren);
    return roots;
  }

  private void sortChildren(SectionTreeNode node) {
    node.getChildren().sort(Comparator.comparing(SectionTreeNode::getSortIndex));
    node.getChildren().forEach(this::sortChildren);
  }

  @Transactional
  public SectionTreeNode create(Long documentId, SectionCreateRequest request) {
    Document document = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    Section parent = null;
    if (request.getParentId() != null) {
      parent = sectionRepository.findById(request.getParentId()).orElseThrow(EntityNotFoundException::new);
    }
    Section section = Section.builder()
        .document(document)
        .parent(parent)
        .title(request.getTitle())
        .level(request.getLevel())
        .sortIndex(request.getSortIndex())
        .status(SectionStatus.DRAFT)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionRepository.save(section);
    return toNode(section);
  }

  @Transactional
  public SectionTreeNode update(Long sectionId, SectionUpdateRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    if (request.getTitle() != null) section.setTitle(request.getTitle());
    if (request.getSortIndex() != null) section.setSortIndex(request.getSortIndex());
    if (request.getStatus() != null) section.setStatus(SectionStatus.valueOf(request.getStatus()));
    return toNode(section);
  }

  @Transactional
  public void delete(Long sectionId) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    deleteChildren(section);
    sectionRepository.delete(section);
  }

  private void deleteChildren(Section section) {
    List<Section> children = sectionRepository.findByParentIdOrderBySortIndexAsc(section.getId());
    for (Section child : children) {
      deleteChildren(child);
      sectionRepository.delete(child);
    }
  }

  @Transactional
  public SectionTreeNode move(Long sectionId, SectionMoveRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    Section parent = null;
    if (request.getTargetParentId() != null) {
      parent = sectionRepository.findById(request.getTargetParentId()).orElseThrow(EntityNotFoundException::new);
    }
    section.setParent(parent);
    section.setSortIndex(request.getTargetSortIndex());
    return toNode(section);
  }

  @Transactional
  public void lock(Long sectionId) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    Long userId = currentUserService.getCurrentUserId();
    if (section.getLockedBy() != null && !section.getLockedBy().equals(userId)) {
      throw new IllegalStateException("Section is locked by another user");
    }
    section.setLockedBy(userId);
    section.setLockedAt(Instant.now());
  }

  @Transactional
  public void unlock(Long sectionId) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    Long userId = currentUserService.getCurrentUserId();
    if (section.getLockedBy() != null && !section.getLockedBy().equals(userId)) {
      throw new IllegalStateException("Only lock owner can unlock");
    }
    section.setLockedBy(null);
    section.setLockedAt(null);
  }

  @Transactional
  public SectionVersionResponse createVersion(Long sectionId, SectionVersionCreateRequest request, SectionSourceType sourceType, String sourceRef) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    Long userId = currentUserService.getCurrentUserId();
    if (section.getLockedBy() != null && !section.getLockedBy().equals(userId)) {
      throw new IllegalStateException("Section is locked by another user");
    }
    SectionVersion version = section.getCurrentVersion();
    if (version == null) {
      version = SectionVersion.builder()
          .section(section)
          .content(request.getContent())
          .summary(request.getSummary())
          .sourceType(sourceType)
          .sourceRef(sourceRef)
          .createdBy(userId)
          .build();
    } else {
      // 章节层只维护当前版本内容，不再持续新增历史记录
      version.setContent(request.getContent());
      version.setSummary(request.getSummary());
      version.setSourceType(sourceType);
      version.setSourceRef(sourceRef);
      version.setCreatedBy(userId);
    }
    sectionVersionRepository.save(version);
    section.setCurrentVersion(version);
    return toVersionResponse(version);
  }

  public List<SectionVersionResponse> listVersions(Long sectionId) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    if (section.getCurrentVersion() == null) {
      return List.of();
    }
    return List.of(toVersionResponse(section.getCurrentVersion()));
  }

  public SectionVersionResponse getVersion(Long sectionId, Long versionId) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    SectionVersion version = section.getCurrentVersion();
    if (version == null) {
      throw new EntityNotFoundException();
    }
    if (!version.getSection().getId().equals(sectionId)) {
      throw new IllegalArgumentException("Version does not belong to section");
    }
    return toVersionResponse(version);
  }

  private SectionTreeNode toNode(Section section) {
    SectionTreeNode node = new SectionTreeNode();
    node.setId(section.getId());
    node.setDocumentId(section.getDocument().getId());
    node.setParentId(section.getParent() == null ? null : section.getParent().getId());
    node.setTitle(section.getTitle());
    node.setLevel(section.getLevel());
    node.setSortIndex(section.getSortIndex());
    node.setCurrentVersionId(section.getCurrentVersion() == null ? null : section.getCurrentVersion().getId());
    node.setStatus(section.getStatus());
    return node;
  }

  private SectionVersionResponse toVersionResponse(SectionVersion version) {
    return SectionVersionResponse.builder()
        .id(version.getId())
        .sectionId(version.getSection().getId())
        .content(version.getContent())
        .summary(version.getSummary())
        .sourceType(version.getSourceType())
        .sourceRef(version.getSourceRef())
        .createdBy(version.getCreatedBy())
        .createdAt(version.getCreatedAt())
        .build();
  }
}
