package com.bidcollab.controller;

import com.bidcollab.dto.KnowledgeBaseCreateRequest;
import com.bidcollab.dto.KnowledgeBaseResponse;
import com.bidcollab.dto.KnowledgeChunkResponse;
import com.bidcollab.dto.KnowledgeDocumentGrantRequest;
import com.bidcollab.dto.KnowledgeDocumentResponse;
import com.bidcollab.dto.KnowledgeDocumentVisibilityUpdateRequest;
import com.bidcollab.dto.KnowledgeManualContentRequest;
import com.bidcollab.dto.KnowledgeSearchRequest;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.dto.graph.KnowledgeGraphNodeDetailResponse;
import com.bidcollab.dto.graph.KnowledgeGraphResponse;
import com.bidcollab.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
  private final KnowledgeBaseService service;

  public KnowledgeBaseController(KnowledgeBaseService service) {
    this.service = service;
  }

  @PostMapping
  public KnowledgeBaseResponse create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
    return service.create(request);
  }

  @GetMapping
  public List<KnowledgeBaseResponse> list() {
    return service.list();
  }

  @DeleteMapping("/{kbId}")
  public void delete(@PathVariable("kbId") Long kbId) {
    service.delete(kbId);
  }

  @PostMapping("/{kbId}/contents")
  public KnowledgeDocumentResponse addManual(@PathVariable("kbId") Long kbId, @Valid @RequestBody KnowledgeManualContentRequest request) {
    return service.addManualContent(kbId, request);
  }

  @PostMapping(path = "/{kbId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public KnowledgeDocumentResponse upload(@PathVariable("kbId") Long kbId, @RequestParam("file") MultipartFile file) {
    return service.upload(kbId, file);
  }

  @PostMapping("/{kbId}/reindex")
  public void reindex(@PathVariable("kbId") Long kbId) {
    service.reindexKnowledgeBase(kbId);
  }

  @GetMapping("/{kbId}/documents")
  public List<KnowledgeDocumentResponse> listDocuments(@PathVariable("kbId") Long kbId) {
    return service.listDocuments(kbId);
  }

  @PostMapping("/{kbId}/documents/{documentId}/visibility")
  public KnowledgeDocumentResponse updateVisibility(@PathVariable("kbId") Long kbId,
                                                    @PathVariable("documentId") Long documentId,
                                                    @Valid @RequestBody KnowledgeDocumentVisibilityUpdateRequest request) {
    return service.updateVisibility(kbId, documentId, request);
  }

  @PostMapping("/{kbId}/documents/{documentId}/permissions")
  public void grantAccess(@PathVariable("kbId") Long kbId,
                          @PathVariable("documentId") Long documentId,
                          @Valid @RequestBody KnowledgeDocumentGrantRequest request) {
    service.grantAccess(kbId, documentId, request);
  }

  @GetMapping("/{kbId}/chunks")
  public List<KnowledgeChunkResponse> listChunks(@PathVariable("kbId") Long kbId) {
    return service.listChunks(kbId);
  }

  @GetMapping("/{kbId}/graph")
  public KnowledgeGraphResponse graph(@PathVariable("kbId") Long kbId) {
    return service.graph(kbId);
  }

  @GetMapping("/{kbId}/graph/node-detail")
  public KnowledgeGraphNodeDetailResponse graphNodeDetail(@PathVariable("kbId") Long kbId,
                                                          @RequestParam("nodeId") String nodeId) {
    return service.graphNodeDetail(kbId, nodeId);
  }

  @PostMapping("/{kbId}/search")
  public List<KnowledgeSearchResult> search(@PathVariable("kbId") Long kbId, @Valid @RequestBody KnowledgeSearchRequest request) {
    return service.search(kbId, request);
  }
}
