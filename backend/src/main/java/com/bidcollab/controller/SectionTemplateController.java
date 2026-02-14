package com.bidcollab.controller;

import com.bidcollab.dto.SectionTemplateApplyRequest;
import com.bidcollab.dto.SectionTemplateCreateFromDocumentRequest;
import com.bidcollab.dto.SectionTemplateResponse;
import com.bidcollab.service.SectionTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SectionTemplateController {
  private final SectionTemplateService sectionTemplateService;

  public SectionTemplateController(SectionTemplateService sectionTemplateService) {
    this.sectionTemplateService = sectionTemplateService;
  }

  @GetMapping("/section-templates")
  public List<SectionTemplateResponse> list() {
    return sectionTemplateService.list();
  }

  @PostMapping("/section-templates/from-document")
  public SectionTemplateResponse createFromDocument(@Valid @RequestBody SectionTemplateCreateFromDocumentRequest request) {
    return sectionTemplateService.createFromDocument(request);
  }

  @PostMapping("/documents/{documentId}/section-templates/{templateId}/apply")
  public void apply(@PathVariable("documentId") Long documentId,
      @PathVariable("templateId") Long templateId,
      @RequestBody(required = false) SectionTemplateApplyRequest request) {
    sectionTemplateService.applyToDocument(templateId, documentId, request != null && request.isClearExisting());
  }
}

