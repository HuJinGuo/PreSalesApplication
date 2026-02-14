package com.bidcollab.service;

import com.bidcollab.dto.SectionTemplateCreateFromDocumentRequest;
import com.bidcollab.dto.SectionTemplateNode;
import com.bidcollab.dto.SectionTemplateResponse;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionTemplate;
import com.bidcollab.enums.SectionStatus;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionTemplateService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SectionTemplateRepository sectionTemplateRepository;
  private final SectionRepository sectionRepository;
  private final DocumentRepository documentRepository;
  private final CurrentUserService currentUserService;

  public SectionTemplateService(SectionTemplateRepository sectionTemplateRepository,
      SectionRepository sectionRepository,
      DocumentRepository documentRepository,
      CurrentUserService currentUserService) {
    this.sectionTemplateRepository = sectionTemplateRepository;
    this.sectionRepository = sectionRepository;
    this.documentRepository = documentRepository;
    this.currentUserService = currentUserService;
  }

  public List<SectionTemplateResponse> list() {
    return sectionTemplateRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Transactional
  public SectionTemplateResponse createFromDocument(SectionTemplateCreateFromDocumentRequest request) {
    List<SectionTemplateNode> structure = extractStructureFromDocument(request.getDocumentId());
    if (structure.isEmpty()) {
      throw new IllegalArgumentException("Document has no section nodes");
    }
    SectionTemplate template = SectionTemplate.builder()
        .name(request.getName().trim())
        .description(request.getDescription())
        .structureJson(writeStructure(structure))
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionTemplateRepository.save(template);
    return toResponse(template);
  }

  @Transactional
  public void applyToDocument(Long templateId, Long documentId, boolean clearExisting) {
    SectionTemplate template = sectionTemplateRepository.findById(templateId).orElseThrow(EntityNotFoundException::new);
    Document document = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    List<SectionTemplateNode> nodes = readStructure(template.getStructureJson());
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Template has empty structure");
    }
    if (clearExisting) {
      throw new IllegalArgumentException("clearExisting is not supported currently to avoid deleting versioned sections");
    }
    int rootStartSort = nextSortIndex(documentId, null);
    for (int i = 0; i < nodes.size(); i++) {
      createFromNode(document, null, nodes.get(i), 1, rootStartSort + i);
    }
  }

  private Section createFromNode(Document document, Section parent, SectionTemplateNode node, int level, int sortIndex) {
    Section section = Section.builder()
        .document(document)
        .parent(parent)
        .title(node.getTitle() == null ? "未命名章节" : node.getTitle().trim())
        .level(level)
        .sortIndex(sortIndex)
        .status(SectionStatus.DRAFT)
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    sectionRepository.save(section);
    List<SectionTemplateNode> children = node.getChildren() == null ? List.of() : node.getChildren();
    int childSort = 1;
    for (SectionTemplateNode child : children) {
      createFromNode(document, section, child, level + 1, childSort++);
    }
    return section;
  }

  private int nextSortIndex(Long documentId, Long parentId) {
    List<Section> sections = sectionRepository.findByDocumentIdOrderBySortIndexAsc(documentId);
    return sections.stream()
        .filter(s -> {
          Long p = s.getParent() == null ? null : s.getParent().getId();
          return (p == null && parentId == null) || (p != null && p.equals(parentId));
        })
        .map(Section::getSortIndex)
        .max(Integer::compareTo)
        .orElse(0) + 1;
  }

  private List<SectionTemplateNode> extractStructureFromDocument(Long documentId) {
    List<Section> sections = sectionRepository.findByDocumentIdOrderBySortIndexAsc(documentId);
    Map<Long, SectionTemplateNode> map = new HashMap<>();
    List<SectionTemplateNode> roots = new ArrayList<>();
    sections.forEach(section -> {
      SectionTemplateNode node = new SectionTemplateNode();
      node.setTitle(section.getTitle());
      map.put(section.getId(), node);
    });
    sections.stream().sorted(Comparator.comparing(Section::getSortIndex)).forEach(section -> {
      SectionTemplateNode node = map.get(section.getId());
      if (section.getParent() == null) {
        roots.add(node);
      } else {
        SectionTemplateNode parent = map.get(section.getParent().getId());
        if (parent != null) {
          parent.getChildren().add(node);
        }
      }
    });
    return roots;
  }

  private String writeStructure(List<SectionTemplateNode> nodes) {
    try {
      return MAPPER.writeValueAsString(nodes);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize template structure", ex);
    }
  }

  private List<SectionTemplateNode> readStructure(String structureJson) {
    try {
      return MAPPER.readValue(structureJson, new TypeReference<List<SectionTemplateNode>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse template structure", ex);
    }
  }

  private SectionTemplateResponse toResponse(SectionTemplate template) {
    return SectionTemplateResponse.builder()
        .id(template.getId())
        .name(template.getName())
        .description(template.getDescription())
        .structure(readStructure(template.getStructureJson()))
        .createdAt(template.getCreatedAt())
        .build();
  }
}

