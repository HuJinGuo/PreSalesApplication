package com.bidcollab.service;

import com.bidcollab.dto.AssetCreateRequest;
import com.bidcollab.dto.AssetResponse;
import com.bidcollab.dto.ReuseRequest;
import com.bidcollab.dto.SectionVersionCreateRequest;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionAsset;
import com.bidcollab.entity.SectionReuseTrace;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.enums.SectionStatus;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionAssetRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionReuseTraceRepository;
import com.bidcollab.repository.SectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {
  private final SectionAssetRepository sectionAssetRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final SectionReuseTraceRepository reuseTraceRepository;
  private final DocumentRepository documentRepository;
  private final CurrentUserService currentUserService;
  public AssetService(SectionAssetRepository sectionAssetRepository,
                      SectionRepository sectionRepository,
                      SectionVersionRepository sectionVersionRepository,
                      SectionReuseTraceRepository reuseTraceRepository,
                      DocumentRepository documentRepository,
                      CurrentUserService currentUserService) {
    this.sectionAssetRepository = sectionAssetRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.reuseTraceRepository = reuseTraceRepository;
    this.documentRepository = documentRepository;
    this.currentUserService = currentUserService;
  }

  @Transactional
  public AssetResponse createAsset(Long sectionId, AssetCreateRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    SectionVersion version = sectionVersionRepository.findById(request.getVersionId())
        .orElseThrow(EntityNotFoundException::new);
    SectionAsset asset = SectionAsset.builder()
        .section(section)
        .version(version)
        .industryTag(request.getIndustryTag())
        .scopeTag(request.getScopeTag())
        .isWinning(request.getIsWinning())
        .keywords(request.getKeywords())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionAssetRepository.save(asset);
    return toResponse(asset);
  }

  public List<AssetResponse> search(String industry, String scope, String keyword) {
    String industryTag = industry == null ? "" : industry;
    String scopeTag = scope == null ? "" : scope;
    String keywords = keyword == null ? "" : keyword;
    return sectionAssetRepository
        .findByIndustryTagContainingAndScopeTagContainingAndKeywordsContaining(industryTag, scopeTag, keywords)
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Transactional
  public Long reuse(ReuseRequest request) {
    SectionAsset asset = sectionAssetRepository.findById(request.getAssetId())
        .orElseThrow(EntityNotFoundException::new);
    Document document = documentRepository.findById(request.getDocumentId())
        .orElseThrow(EntityNotFoundException::new);
    Section parent = null;
    if (request.getTargetParentId() != null) {
      parent = sectionRepository.findById(request.getTargetParentId()).orElseThrow(EntityNotFoundException::new);
    }

    Section newSection = Section.builder()
        .document(document)
        .parent(parent)
        .title(asset.getSection().getTitle())
        .level(request.getTargetLevel())
        .sortIndex(request.getTargetSortIndex())
        .status(SectionStatus.DRAFT)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionRepository.save(newSection);

    SectionVersion sourceVersion = asset.getVersion();
    SectionVersionCreateRequest versionRequest = new SectionVersionCreateRequest();
    versionRequest.setContent(sourceVersion.getContent());
    versionRequest.setSummary(sourceVersion.getSummary());
    SectionVersion newVersion = SectionVersion.builder()
        .section(newSection)
        .content(versionRequest.getContent())
        .summary(versionRequest.getSummary())
        .sourceType(SectionSourceType.REUSE)
        .sourceRef(String.valueOf(asset.getId()))
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionVersionRepository.save(newVersion);
    newSection.setCurrentVersion(newVersion);

    SectionReuseTrace trace = SectionReuseTrace.builder()
        .targetSectionId(newSection.getId())
        .targetVersionId(newVersion.getId())
        .sourceProjectId(asset.getSection().getDocument().getProject().getId())
        .sourceDocumentId(asset.getSection().getDocument().getId())
        .sourceSectionId(asset.getSection().getId())
        .sourceVersionId(asset.getVersion().getId())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    reuseTraceRepository.save(trace);

    return newSection.getId();
  }

  public List<SectionReuseTrace> listReuseTrace(Long sectionId) {
    return reuseTraceRepository.findByTargetSectionIdOrderByCreatedAtDesc(sectionId);
  }

  private AssetResponse toResponse(SectionAsset asset) {
    return AssetResponse.builder()
        .id(asset.getId())
        .sectionId(asset.getSection().getId())
        .versionId(asset.getVersion().getId())
        .industryTag(asset.getIndustryTag())
        .scopeTag(asset.getScopeTag())
        .isWinning(asset.getIsWinning())
        .keywords(asset.getKeywords())
        .build();
  }
}
