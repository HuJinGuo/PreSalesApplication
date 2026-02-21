package com.bidcollab.service;

import com.bidcollab.dto.DocumentCreateRequest;
import com.bidcollab.dto.DocumentResponse;
import com.bidcollab.dto.DocumentUpdateRequest;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Project;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.repository.DocumentExportRepository;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.ProjectRepository;
import com.bidcollab.repository.SectionAssetRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionReuseTraceRepository;
import com.bidcollab.repository.SectionReviewRepository;
import com.bidcollab.repository.SectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final SectionAssetRepository sectionAssetRepository;
  private final SectionReviewRepository sectionReviewRepository;
  private final SectionReuseTraceRepository sectionReuseTraceRepository;
  private final DocumentExportRepository documentExportRepository;
  private final CurrentUserService currentUserService;

  public DocumentService(DocumentRepository documentRepository,
                         ProjectRepository projectRepository,
                         SectionRepository sectionRepository,
                         SectionVersionRepository sectionVersionRepository,
                         SectionAssetRepository sectionAssetRepository,
                         SectionReviewRepository sectionReviewRepository,
                         SectionReuseTraceRepository sectionReuseTraceRepository,
                         DocumentExportRepository documentExportRepository,
                         CurrentUserService currentUserService) {
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.sectionAssetRepository = sectionAssetRepository;
    this.sectionReviewRepository = sectionReviewRepository;
    this.sectionReuseTraceRepository = sectionReuseTraceRepository;
    this.documentExportRepository = documentExportRepository;
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
  public void delete(Long id) {
    Document document = documentRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    List<Section> sections = sectionRepository.findTreeByDocumentId(id);
    if (!sections.isEmpty()) {
      List<Long> sectionIds = sections.stream().map(Section::getId).toList();
      List<SectionVersion> sectionVersions = sectionVersionRepository.findBySectionIdIn(sectionIds);
      List<Long> versionIds = sectionVersions.stream().map(SectionVersion::getId).toList();

      sectionRepository.clearCurrentVersionByDocumentId(id);
      sectionReuseTraceRepository.deleteBySectionIds(sectionIds);
      if (!versionIds.isEmpty()) {
        sectionReuseTraceRepository.deleteByVersionIds(versionIds);
        sectionAssetRepository.deleteByVersionIdIn(versionIds);
        sectionReviewRepository.deleteByVersionIdIn(versionIds);
      }
      sectionAssetRepository.deleteBySectionIdIn(sectionIds);
      sectionReviewRepository.deleteBySectionIdIn(sectionIds);
      sectionVersionRepository.deleteBySectionIdIn(sectionIds);

      sections.sort((a, b) -> Integer.compare(b.getLevel(), a.getLevel()));
      for (Section section : sections) {
        sectionRepository.delete(section);
      }
    }

    documentExportRepository.deleteByDocumentId(id);
    documentRepository.delete(document);
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
