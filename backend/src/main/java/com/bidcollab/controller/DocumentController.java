package com.bidcollab.controller;

import com.bidcollab.dto.DocumentCreateRequest;
import com.bidcollab.dto.DocumentResponse;
import com.bidcollab.dto.DocumentUpdateRequest;
import com.bidcollab.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DocumentController {
  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  @PostMapping("/projects/{projectId}/documents")
  public DocumentResponse create(@PathVariable("projectId") Long projectId, @Valid @RequestBody DocumentCreateRequest request) {
    return documentService.create(projectId, request);
  }

  @GetMapping("/projects/{projectId}/documents")
  public List<DocumentResponse> list(@PathVariable("projectId") Long projectId) {
    return documentService.listByProject(projectId);
  }

  @GetMapping("/documents/{id}")
  public DocumentResponse get(@PathVariable("id") Long id) {
    return documentService.get(id);
  }

  @PutMapping("/documents/{id}")
  public DocumentResponse update(@PathVariable("id") Long id, @RequestBody DocumentUpdateRequest request) {
    return documentService.update(id, request);
  }

  @DeleteMapping("/documents/{id}")
  public void delete(@PathVariable("id") Long id) {
    documentService.delete(id);
  }

}
