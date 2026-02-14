package com.bidcollab.service;

import com.bidcollab.dto.DocumentCreateRequest;
import com.bidcollab.dto.DocumentResponse;
import com.bidcollab.dto.DocumentUpdateRequest;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Project;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final CurrentUserService currentUserService;

  public DocumentService(DocumentRepository documentRepository, ProjectRepository projectRepository,
                         CurrentUserService currentUserService) {
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
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
