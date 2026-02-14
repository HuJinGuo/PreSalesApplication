package com.bidcollab.service;

import com.bidcollab.dto.ExportResponse;
import com.bidcollab.entity.Document;
import com.bidcollab.entity.DocumentExport;
import com.bidcollab.entity.Section;
import com.bidcollab.enums.ExportStatus;
import com.bidcollab.export.DocxExporter;
import com.bidcollab.export.ExportImageLoader;
import com.bidcollab.export.FlattenedSection;
import com.bidcollab.export.PdfExporter;
import com.bidcollab.repository.DocumentExportRepository;
import com.bidcollab.repository.DocumentRepository;
import com.bidcollab.repository.SectionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {
  private final DocumentRepository documentRepository;
  private final SectionRepository sectionRepository;
  private final DocumentExportRepository exportRepository;
  private final CurrentUserService currentUserService;
  private final String baseDir;
  private final String uploadDir;

  public ExportService(DocumentRepository documentRepository,
                       SectionRepository sectionRepository,
                       DocumentExportRepository exportRepository,
                       CurrentUserService currentUserService,
                       @Value("${app.export.base-dir}") String baseDir,
                       @Value("${app.upload.storage-dir:/tmp/bid-doc-uploads}") String uploadDir) {
    this.documentRepository = documentRepository;
    this.sectionRepository = sectionRepository;
    this.exportRepository = exportRepository;
    this.currentUserService = currentUserService;
    this.baseDir = baseDir;
    this.uploadDir = uploadDir;
  }

  @Transactional
  public ExportResponse export(Long documentId, String format) {
    Document document = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);
    DocumentExport export = DocumentExport.builder()
        .documentId(documentId)
        .format(format)
        .status(ExportStatus.RUNNING)
        .startedAt(Instant.now())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    exportRepository.save(export);

    try {
      List<Section> sections = sectionRepository.findByDocumentIdOrderBySortIndexAsc(documentId);
      List<FlattenedSection> flattened = flatten(sections);
      String fileName = "document-" + documentId + "-" + export.getId() + "." + format;
      Path outputPath = Path.of(baseDir, fileName);
      ExportImageLoader imageLoader = new ExportImageLoader(uploadDir);
      if ("docx".equalsIgnoreCase(format)) {
        new DocxExporter(imageLoader).export(document.getName(), flattened, outputPath);
      } else if ("pdf".equalsIgnoreCase(format)) {
        new PdfExporter(imageLoader).export(document.getName(), flattened, outputPath);
      } else {
        throw new IllegalArgumentException("Unsupported format: " + format);
      }
      export.setStatus(ExportStatus.SUCCESS);
      export.setFilePath(outputPath.toString());
      export.setFinishedAt(Instant.now());
    } catch (Exception ex) {
      export.setStatus(ExportStatus.FAILED);
      export.setErrorMessage(ex.getMessage());
      export.setFinishedAt(Instant.now());
    }
    return toResponse(export);
  }

  public DocumentExport getExport(Long exportId) {
    return exportRepository.findById(exportId).orElseThrow(EntityNotFoundException::new);
  }

  public List<DocumentExport> listExports(Long documentId) {
    return exportRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
  }

  private List<FlattenedSection> flatten(List<Section> sections) {
    Map<Long, List<Section>> childrenMap = new HashMap<>();
    List<Section> roots = new ArrayList<>();
    for (Section section : sections) {
      if (section.getParent() == null) {
        roots.add(section);
      } else {
        childrenMap.computeIfAbsent(section.getParent().getId(), k -> new ArrayList<>()).add(section);
      }
    }
    List<FlattenedSection> result = new ArrayList<>();
    int[] counters = new int[10];
    for (Section root : roots) {
      traverse(root, childrenMap, result, counters, 1);
    }
    return result;
  }

  private void traverse(Section section, Map<Long, List<Section>> childrenMap, List<FlattenedSection> result,
                        int[] counters, int level) {
    counters[level - 1]++;
    for (int i = level; i < counters.length; i++) counters[i] = 0;
    String number = buildNumber(counters, level);
    String content = section.getCurrentVersion() == null ? "" : section.getCurrentVersion().getContent();
    result.add(new FlattenedSection(number, section.getTitle(), content, level));
    List<Section> children = childrenMap.getOrDefault(section.getId(), List.of());
    children.sort((a, b) -> a.getSortIndex().compareTo(b.getSortIndex()));
    for (Section child : children) {
      traverse(child, childrenMap, result, counters, level + 1);
    }
  }

  private String buildNumber(int[] counters, int level) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < level; i++) {
      if (i > 0) sb.append('.');
      sb.append(counters[i]);
    }
    return sb.toString();
  }

  private ExportResponse toResponse(DocumentExport export) {
    return ExportResponse.builder()
        .id(export.getId())
        .documentId(export.getDocumentId())
        .format(export.getFormat())
        .status(export.getStatus())
        .filePath(export.getFilePath())
        .createdAt(export.getCreatedAt())
        .build();
  }
}
