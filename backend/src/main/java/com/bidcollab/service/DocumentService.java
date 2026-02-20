package com.bidcollab.service;

import com.bidcollab.dto.DocumentCreateRequest;
import com.bidcollab.dto.DocumentResponse;
import com.bidcollab.dto.DocumentUpdateRequest;
import com.bidcollab.dto.DocumentVersionCreateRequest;
import com.bidcollab.dto.DocumentVersionResponse;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.DocumentVersion;
import com.bidcollab.entity.Project;
import com.bidcollab.entity.Section;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.DocumentVersionRepository;
import com.bidcollab.repository.ProjectRepository;
import com.bidcollab.repository.SectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final SectionRepository sectionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final ObjectMapper objectMapper;
  private final CurrentUserService currentUserService;

  public DocumentService(DocumentRepository documentRepository,
                         ProjectRepository projectRepository,
                         SectionRepository sectionRepository,
                         DocumentVersionRepository documentVersionRepository,
                         ObjectMapper objectMapper,
                         CurrentUserService currentUserService) {
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.sectionRepository = sectionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.objectMapper = objectMapper;
    this.currentUserService = currentUserService;
  }

  @Transactional
  public DocumentResponse create(Long projectId, DocumentCreateRequest request) {
    Project project = projectRepository.findById(projectId).orElseThrow(EntityNotFoundException::new);
    Document document = Document.builder()
        .project(project)
        .name(request.getName())
        .docType(request.getDocType())
        .versionNo(1)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentRepository.save(document);
    return toResponse(document);
  }

  public List<DocumentResponse> listByProject(Long projectId) {
    return documentRepository.findByProjectId(projectId).stream()
        .map(this::toResponse).collect(Collectors.toList());
  }

  public DocumentResponse get(Long id) {
    return toResponse(documentRepository.findById(id).orElseThrow(EntityNotFoundException::new));
  }

  @Transactional
  public DocumentResponse update(Long id, DocumentUpdateRequest request) {
    Document document = documentRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    if (request.getName() != null) document.setName(request.getName());
    if (request.getDocType() != null) document.setDocType(request.getDocType());
    if (request.getVersionNo() != null) document.setVersionNo(request.getVersionNo());
    return toResponse(document);
  }

  @Transactional
  public DocumentVersionResponse createVersion(Long documentId, DocumentVersionCreateRequest request) {
    Document document = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    String snapshotJson = buildSnapshot(documentId);
    DocumentVersion version = DocumentVersion.builder()
        .documentId(documentId)
        .summary(request == null ? null : request.getSummary())
        .snapshotJson(snapshotJson)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    documentVersionRepository.save(version);
    document.setVersionNo((document.getVersionNo() == null ? 0 : document.getVersionNo()) + 1);
    return toVersionResponse(version, true);
  }

  public List<DocumentVersionResponse> listVersions(Long documentId) {
    documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    return documentVersionRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
        .map(v -> toVersionResponse(v, false))
        .collect(Collectors.toList());
  }

  public DocumentVersionResponse getVersion(Long documentId, Long versionId) {
    documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    DocumentVersion version = documentVersionRepository.findById(versionId).orElseThrow(EntityNotFoundException::new);
    if (!version.getDocumentId().equals(documentId)) {
      throw new IllegalArgumentException("Version does not belong to document");
    }
    return toVersionResponse(version, true);
  }

  private String buildSnapshot(Long documentId) {
    List<Section> sections = sectionRepository.findForExportByDocumentId(documentId);
    List<Map<String, Object>> sectionSnapshots = new ArrayList<>();
    for (Section section : sections) {
      Map<String, Object> item = new java.util.LinkedHashMap<>();
      item.put("sectionId", section.getId());
      item.put("parentId", section.getParent() == null ? null : section.getParent().getId());
      item.put("title", section.getTitle());
      item.put("level", section.getLevel());
      item.put("sortIndex", section.getSortIndex());
      item.put("status", section.getStatus() == null ? null : section.getStatus().name());
      item.put("content", section.getCurrentVersion() == null ? "" : section.getCurrentVersion().getContent());
      sectionSnapshots.add(item);
    }
    try {
      return objectMapper.writeValueAsString(Map.of("sections", sectionSnapshots));
    } catch (Exception ex) {
      throw new IllegalStateException("无法生成文档版本快照", ex);
    }
  }

  private DocumentVersionResponse toVersionResponse(DocumentVersion version, boolean includeSnapshot) {
    return DocumentVersionResponse.builder()
        .id(version.getId())
        .documentId(version.getDocumentId())
        .summary(version.getSummary())
        .snapshotJson(includeSnapshot ? version.getSnapshotJson() : null)
        .createdBy(version.getCreatedBy())
        .createdAt(version.getCreatedAt())
        .build();
  }

  private DocumentResponse toResponse(Document document) {
    return DocumentResponse.builder()
        .id(document.getId())
        .projectId(document.getProject().getId())
        .name(document.getName())
        .docType(document.getDocType())
        .versionNo(document.getVersionNo())
        .createdAt(document.getCreatedAt())
        .updatedAt(document.getUpdatedAt())
        .build();
  }
}
