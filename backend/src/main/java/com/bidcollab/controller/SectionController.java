package com.bidcollab.controller;

import com.bidcollab.dto.SectionCreateRequest;
import com.bidcollab.dto.SectionChunkRefResponse;
import com.bidcollab.dto.SectionMoveRequest;
import com.bidcollab.dto.SectionTreeNode;
import com.bidcollab.dto.SectionUpdateRequest;
import com.bidcollab.dto.SectionVersionCreateRequest;
import com.bidcollab.dto.SectionVersionResponse;
import com.bidcollab.enums.SectionSourceType;
import com.bidcollab.service.SectionService;
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
public class SectionController {
  private final SectionService sectionService;

  public SectionController(SectionService sectionService) {
    this.sectionService = sectionService;
  }

  @GetMapping("/documents/{documentId}/sections/tree")
  public List<SectionTreeNode> tree(@PathVariable("documentId") Long documentId) {
    return sectionService.getTree(documentId);
  }

  @PostMapping("/documents/{documentId}/sections")
  public SectionTreeNode create(@PathVariable("documentId") Long documentId, @Valid @RequestBody SectionCreateRequest request) {
    return sectionService.create(documentId, request);
  }

  @PutMapping("/sections/{id}")
  public SectionTreeNode update(@PathVariable("id") Long id, @RequestBody SectionUpdateRequest request) {
    return sectionService.update(id, request);
  }

  @DeleteMapping("/sections/{id}")
  public void delete(@PathVariable("id") Long id) {
    sectionService.delete(id);
  }

  @PostMapping("/sections/{id}/move")
  public SectionTreeNode move(@PathVariable("id") Long id, @Valid @RequestBody SectionMoveRequest request) {
    return sectionService.move(id, request);
  }

  @PostMapping("/sections/{id}/lock")
  public void lock(@PathVariable("id") Long id) {
    sectionService.lock(id);
  }

  @PostMapping("/sections/{id}/unlock")
  public void unlock(@PathVariable("id") Long id) {
    sectionService.unlock(id);
  }

  @GetMapping("/sections/{id}/versions")
  public List<SectionVersionResponse> listVersions(@PathVariable("id") Long id) {
    return sectionService.listVersions(id);
  }

  @GetMapping("/sections/{id}/versions/{versionId}")
  public SectionVersionResponse getVersion(@PathVariable("id") Long id, @PathVariable("versionId") Long versionId) {
    return sectionService.getVersion(id, versionId);
  }

  @PostMapping("/sections/{id}/versions")
  public SectionVersionResponse createVersion(@PathVariable("id") Long id, @Valid @RequestBody SectionVersionCreateRequest request) {
    return sectionService.createVersion(id, request, SectionSourceType.MANUAL, null);
  }

  @GetMapping("/sections/{id}/chunk-refs")
  public List<SectionChunkRefResponse> listChunkRefs(@PathVariable("id") Long id) {
    return sectionService.listChunkRefs(id);
  }
}
