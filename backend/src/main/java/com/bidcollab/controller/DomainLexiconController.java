package com.bidcollab.controller;

import com.bidcollab.dto.DomainLexiconUpsertRequest;
import com.bidcollab.dto.DomainLexiconUpdateRequest;
import com.bidcollab.dto.KnowledgeLexiconResponse;
import com.bidcollab.service.DomainLexiconService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/domain-lexicons")
public class DomainLexiconController {
  private final DomainLexiconService service;

  public DomainLexiconController(DomainLexiconService service) {
    this.service = service;
  }

  @GetMapping
  public List<KnowledgeLexiconResponse> list(@RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
    return service.list(knowledgeBaseId);
  }

  @PostMapping
  public KnowledgeLexiconResponse upsert(@Valid @RequestBody DomainLexiconUpsertRequest request) {
    return service.upsert(request);
  }

  @PutMapping("/{lexiconId}")
  public KnowledgeLexiconResponse update(@PathVariable("lexiconId") Long lexiconId,
      @Valid @RequestBody DomainLexiconUpdateRequest request) {
    return service.update(lexiconId, request);
  }

  @DeleteMapping("/{lexiconId}")
  public void delete(@PathVariable("lexiconId") Long lexiconId) {
    service.delete(lexiconId);
  }
}
