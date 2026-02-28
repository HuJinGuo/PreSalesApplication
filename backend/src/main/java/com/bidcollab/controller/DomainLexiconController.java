package com.bidcollab.controller;

import com.bidcollab.dto.DictionaryBatchUpsertRequest;
import com.bidcollab.dto.DictionaryBatchUpsertResponse;
import com.bidcollab.dto.DomainCategoryRelationRequest;
import com.bidcollab.dto.DomainCategoryRelationResponse;
import com.bidcollab.dto.DomainCategoryRequest;
import com.bidcollab.dto.DomainCategoryResponse;
import com.bidcollab.dto.DictionaryEntryRequest;
import com.bidcollab.dto.DictionaryEntryResponse;
import com.bidcollab.dto.DictionaryPackRequest;
import com.bidcollab.dto.DictionaryPackResponse;
import com.bidcollab.dto.DomainLexiconUpsertRequest;
import com.bidcollab.dto.DomainLexiconUpdateRequest;
import com.bidcollab.dto.KnowledgeBaseDictionaryPackBindRequest;
import com.bidcollab.dto.KnowledgeBaseDictionaryPackResponse;
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

  @GetMapping("/categories")
  public List<DomainCategoryResponse> listCategories(@RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
    return service.listCategories(activeOnly);
  }

  @PostMapping("/categories")
  public DomainCategoryResponse createCategory(@Valid @RequestBody DomainCategoryRequest request) {
    return service.createCategory(request);
  }

  @PutMapping("/categories/{categoryId}")
  public DomainCategoryResponse updateCategory(@PathVariable("categoryId") Long categoryId,
      @Valid @RequestBody DomainCategoryRequest request) {
    return service.updateCategory(categoryId, request);
  }

  @DeleteMapping("/categories/{categoryId}")
  public void deleteCategory(@PathVariable("categoryId") Long categoryId) {
    service.deleteCategory(categoryId);
  }

  @GetMapping("/category-relations")
  public List<DomainCategoryRelationResponse> listCategoryRelations(
      @RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
    return service.listCategoryRelations(activeOnly);
  }

  @PostMapping("/category-relations")
  public DomainCategoryRelationResponse createCategoryRelation(@Valid @RequestBody DomainCategoryRelationRequest request) {
    return service.createCategoryRelation(request);
  }

  @PutMapping("/category-relations/{relationId}")
  public DomainCategoryRelationResponse updateCategoryRelation(@PathVariable("relationId") Long relationId,
      @Valid @RequestBody DomainCategoryRelationRequest request) {
    return service.updateCategoryRelation(relationId, request);
  }

  @DeleteMapping("/category-relations/{relationId}")
  public void deleteCategoryRelation(@PathVariable("relationId") Long relationId) {
    service.deleteCategoryRelation(relationId);
  }

  // ===== 词典中心（全局词典包） =====
  @GetMapping("/packs")
  public List<DictionaryPackResponse> listPacks() {
    return service.listPacks();
  }

  @PostMapping("/packs")
  public DictionaryPackResponse createPack(@Valid @RequestBody DictionaryPackRequest request) {
    return service.createPack(request);
  }

  @PutMapping("/packs/{packId}")
  public DictionaryPackResponse updatePack(@PathVariable("packId") Long packId, @Valid @RequestBody DictionaryPackRequest request) {
    return service.updatePack(packId, request);
  }

  @DeleteMapping("/packs/{packId}")
  public void deletePack(@PathVariable("packId") Long packId) {
    service.deletePack(packId);
  }

  @GetMapping("/packs/{packId}/entries")
  public List<DictionaryEntryResponse> listPackEntries(@PathVariable("packId") Long packId) {
    return service.listPackEntries(packId);
  }

  @PostMapping("/packs/{packId}/entries")
  public DictionaryEntryResponse upsertPackEntry(@PathVariable("packId") Long packId,
      @Valid @RequestBody DictionaryEntryRequest request) {
    return service.upsertPackEntry(packId, request, "MANUAL");
  }

  @PutMapping("/entries/{entryId}")
  public DictionaryEntryResponse updatePackEntry(@PathVariable("entryId") Long entryId,
      @Valid @RequestBody DictionaryEntryRequest request) {
    return service.updatePackEntry(entryId, request);
  }

  @DeleteMapping("/entries/{entryId}")
  public void deletePackEntry(@PathVariable("entryId") Long entryId) {
    service.deletePackEntry(entryId);
  }

  @PostMapping("/packs/{packId}/entries/batch-upsert")
  public DictionaryBatchUpsertResponse batchUpsert(
      @PathVariable("packId") Long packId,
      @Valid @RequestBody DictionaryBatchUpsertRequest request) {
    return service.batchUpsertPackEntries(packId, request);
  }

  // ===== 知识库引入词典包 =====
  @GetMapping("/knowledge-bases/{knowledgeBaseId}/packs")
  public List<KnowledgeBaseDictionaryPackResponse> listKbPacks(@PathVariable("knowledgeBaseId") Long knowledgeBaseId) {
    return service.listKnowledgeBasePacks(knowledgeBaseId);
  }

  @PostMapping("/knowledge-bases/{knowledgeBaseId}/packs/{packId}")
  public KnowledgeBaseDictionaryPackResponse bindPack(
      @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
      @PathVariable("packId") Long packId,
      @RequestBody(required = false) KnowledgeBaseDictionaryPackBindRequest request) {
    if (request == null) {
      request = new KnowledgeBaseDictionaryPackBindRequest();
    }
    return service.bindPackToKnowledgeBase(knowledgeBaseId, packId, request);
  }

  @DeleteMapping("/knowledge-bases/{knowledgeBaseId}/packs/{packId}")
  public void unbindPack(
      @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
      @PathVariable("packId") Long packId) {
    service.unbindPackFromKnowledgeBase(knowledgeBaseId, packId);
  }

  // ===== 兼容旧接口（知识库本地词条） =====
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
