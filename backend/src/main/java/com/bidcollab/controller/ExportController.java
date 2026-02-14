package com.bidcollab.controller;

import com.bidcollab.dto.ExportRequest;
import com.bidcollab.dto.ExportResponse;
import com.bidcollab.entity.DocumentExport;
import com.bidcollab.service.ExportService;
import jakarta.validation.Valid;
import java.io.File;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExportController {
  private final ExportService exportService;

  public ExportController(ExportService exportService) {
    this.exportService = exportService;
  }

  @PostMapping("/documents/{documentId}/export")
  public ExportResponse export(@PathVariable("documentId") Long documentId, @Valid @RequestBody ExportRequest request) {
    return exportService.export(documentId, request.getFormat());
  }

  @GetMapping("/documents/{documentId}/exports")
  public List<DocumentExport> list(@PathVariable("documentId") Long documentId) {
    return exportService.listExports(documentId);
  }

  @GetMapping("/exports/{exportId}/download")
  public ResponseEntity<FileSystemResource> download(@PathVariable("exportId") Long exportId) {
    DocumentExport export = exportService.getExport(exportId);
    if (export.getFilePath() == null) {
      return ResponseEntity.notFound().build();
    }
    File file = new File(export.getFilePath());
    if (!file.exists()) {
      return ResponseEntity.notFound().build();
    }
    MediaType mediaType = export.getFormat().equalsIgnoreCase("pdf")
        ? MediaType.APPLICATION_PDF
        : MediaType.APPLICATION_OCTET_STREAM;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
        .contentLength(file.length())
        .contentType(mediaType)
        .body(new FileSystemResource(file));
  }
}
