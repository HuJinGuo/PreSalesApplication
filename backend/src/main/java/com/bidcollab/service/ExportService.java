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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  private static final Logger log = LoggerFactory.getLogger(ExportService.class);
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

  /**
   * 导出文档为指定格式（docx / pdf）。
   * 注意：不使用方法级 @Transactional，避免导出异常触发事务回滚导致 errorMessage 丢失。
   * 创建记录和更新状态均使用 saveAndFlush 立即持久化。
   */
  public ExportResponse export(Long documentId, String format) {
    Document document = documentRepository.findById(documentId).orElseThrow(EntityNotFoundException::new);

    // 先创建导出记录并立即持久化，确保数据库中有 RUNNING 状态的记录
    DocumentExport export = DocumentExport.builder()
        .documentId(documentId)
        .format(format)
        .status(ExportStatus.RUNNING)
        .startedAt(Instant.now())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    exportRepository.saveAndFlush(export);

    try {
      List<Section> sections = sectionRepository.findForExportByDocumentId(documentId);
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
      log.error("Export failed, documentId={}, exportId={}, format={}", documentId, export.getId(), format, ex);
      export.setStatus(ExportStatus.FAILED);
      export.setErrorMessage(resolveErrorMessage(ex));
      export.setFinishedAt(Instant.now());
    }

    // 无论成功/失败，都立即持久化最终状态
    exportRepository.saveAndFlush(export);
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
    for (int i = level; i < counters.length; i++)
      counters[i] = 0;
    String number = buildNumber(counters, level);
    String content = section.getCurrentVersion() == null ? "" : section.getCurrentVersion().getContent();
    result.add(new FlattenedSection(number, section.getTitle(), content, level));
    List<Section> children = new ArrayList<>(childrenMap.getOrDefault(section.getId(), List.of()));
    children.sort((a, b) -> a.getSortIndex().compareTo(b.getSortIndex()));
    for (Section child : children) {
      traverse(child, childrenMap, result, counters, level + 1);
    }
  }

  private String buildNumber(int[] counters, int level) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < level; i++) {
      if (i > 0)
        sb.append('.');
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
        .errorMessage(export.getErrorMessage())
        .createdAt(export.getCreatedAt())
        .startedAt(export.getStartedAt())
        .finishedAt(export.getFinishedAt())
        .build();
  }

  private String resolveErrorMessage(Exception ex) {
    if (ex == null) {
      return "导出失败：未知异常";
    }
    Throwable t = ex;
    while (t.getCause() != null) {
      t = t.getCause();
    }
    String msg = t.getMessage();
    if (msg != null && !msg.isBlank()) {
      return msg;
    }
    msg = ex.getMessage();
    if (msg != null && !msg.isBlank()) {
      return msg;
    }
    return ex.getClass().getSimpleName();
  }
}
