package com.bidcollab.service;

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
import com.bidcollab.entity.DomainCategory;
import com.bidcollab.entity.DomainCategoryRelation;
import com.bidcollab.entity.DomainDictionaryEntry;
import com.bidcollab.entity.DomainDictionaryPack;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeBaseDictionaryPack;
import com.bidcollab.entity.KnowledgeBaseDomainLexicon;
import com.bidcollab.repository.DomainDictionaryEntryRepository;
import com.bidcollab.repository.DomainDictionaryPackRepository;
import com.bidcollab.repository.DomainCategoryRelationRepository;
import com.bidcollab.repository.DomainCategoryRepository;
import com.bidcollab.repository.KnowledgeBaseDictionaryPackRepository;
import com.bidcollab.repository.KnowledgeBaseDomainLexiconRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DomainLexiconService {
  private final KnowledgeBaseDomainLexiconRepository kbLexiconRepository;
  private final KnowledgeBaseRepository baseRepository;
  private final CurrentUserService currentUserService;
  private final DomainDictionaryPackRepository packRepository;
  private final DomainDictionaryEntryRepository entryRepository;
  private final DomainCategoryRepository categoryRepository;
  private final DomainCategoryRelationRepository categoryRelationRepository;
  private final KnowledgeBaseDictionaryPackRepository kbPackRepository;
  private final ObjectMapper objectMapper;

  public DomainLexiconService(
      KnowledgeBaseDomainLexiconRepository kbLexiconRepository,
      KnowledgeBaseRepository baseRepository,
      CurrentUserService currentUserService,
      DomainDictionaryPackRepository packRepository,
      DomainDictionaryEntryRepository entryRepository,
      DomainCategoryRepository categoryRepository,
      DomainCategoryRelationRepository categoryRelationRepository,
      KnowledgeBaseDictionaryPackRepository kbPackRepository,
      ObjectMapper objectMapper) {
    this.kbLexiconRepository = kbLexiconRepository;
    this.baseRepository = baseRepository;
    this.currentUserService = currentUserService;
    this.packRepository = packRepository;
    this.entryRepository = entryRepository;
    this.categoryRepository = categoryRepository;
    this.categoryRelationRepository = categoryRelationRepository;
    this.kbPackRepository = kbPackRepository;
    this.objectMapper = objectMapper;
  }

  // ===== 旧接口：知识库本地词条（兼容） =====
  public List<KnowledgeLexiconResponse> list(Long knowledgeBaseId) {
    baseRepository.findById(knowledgeBaseId).orElseThrow(EntityNotFoundException::new);
    return kbLexiconRepository.findByKnowledgeBaseIdOrderByCategoryAscTermAsc(knowledgeBaseId).stream()
        .map(this::toKbLexiconResponse)
        .collect(Collectors.toList());
  }

  @Transactional
  public KnowledgeLexiconResponse upsert(DomainLexiconUpsertRequest request) {
    KnowledgeBase kb = baseRepository.findById(request.getKnowledgeBaseId()).orElseThrow(EntityNotFoundException::new);
    DomainCategory categoryRef = resolveCategory(request.getCategoryId(), request.getCategory());
    String category = categoryRef.getCode();
    String term = normalizeTerm(request.getTerm());
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository
        .findByKnowledgeBaseIdAndCategoryRefIdAndTerm(kb.getId(), categoryRef.getId(), term)
        .orElseGet(() -> KnowledgeBaseDomainLexicon.builder()
            .knowledgeBase(kb)
            .categoryRef(categoryRef)
            .category(category)
            .term(term)
            .enabled(Boolean.TRUE)
            .createdBy(currentUserService.getCurrentUserId())
            .build());

    if (request.getEnabled() != null) {
      lexicon.setEnabled(request.getEnabled());
    }
    lexicon.setStandardTerm(normalizeOptional(request.getStandardTerm()));
    kbLexiconRepository.save(lexicon);
    return toKbLexiconResponse(lexicon);
  }

  @Transactional
  public KnowledgeLexiconResponse update(Long lexiconId, DomainLexiconUpdateRequest request) {
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository.findById(lexiconId)
        .orElseThrow(EntityNotFoundException::new);
    if (!lexicon.getKnowledgeBase().getId().equals(request.getKnowledgeBaseId())) {
      throw new IllegalArgumentException("Lexicon does not belong to knowledge base");
    }
    DomainCategory categoryRef = resolveCategory(request.getCategoryId(), request.getCategory());
    String category = categoryRef.getCode();
    String term = normalizeTerm(request.getTerm());
    kbLexiconRepository
        .findByKnowledgeBaseIdAndCategoryRefIdAndTerm(request.getKnowledgeBaseId(), categoryRef.getId(), term)
        .ifPresent(existing -> {
          if (!existing.getId().equals(lexiconId)) {
            throw new IllegalArgumentException("Lexicon term already exists in this category");
          }
        });
    lexicon.setCategoryRef(categoryRef);
    lexicon.setCategory(category);
    lexicon.setTerm(term);
    lexicon.setStandardTerm(normalizeOptional(request.getStandardTerm()));
    if (request.getEnabled() != null) {
      lexicon.setEnabled(request.getEnabled());
    }
    kbLexiconRepository.save(lexicon);
    return toKbLexiconResponse(lexicon);
  }

  @Transactional
  public void delete(Long lexiconId) {
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository.findById(lexiconId)
        .orElseThrow(EntityNotFoundException::new);
    kbLexiconRepository.delete(lexicon);
  }

  // ===== 类别管理 =====
  public List<DomainCategoryResponse> listCategories(boolean activeOnly) {
    List<DomainCategory> categories = activeOnly
        ? categoryRepository.findByStatusOrderBySortOrderAscIdAsc("ACTIVE")
        : categoryRepository.findAllByOrderBySortOrderAscIdAsc();
    return categories.stream().map(this::toCategoryResponse).toList();
  }

  @Transactional
  public DomainCategoryResponse createCategory(DomainCategoryRequest request) {
    String code = normalizeCategory(request.getCode());
    categoryRepository.findByCode(code).ifPresent(existing -> {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别编码已存在");
    });
    DomainCategory category = DomainCategory.builder()
        .code(code)
        .name(request.getName().trim())
        .description(normalizeOptional(request.getDescription()))
        .status(defaultIfBlank(request.getStatus(), "ACTIVE"))
        .sortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder())
        .build();
    return toCategoryResponse(categoryRepository.save(category));
  }

  @Transactional
  public DomainCategoryResponse updateCategory(Long categoryId, DomainCategoryRequest request) {
    DomainCategory category = categoryRepository.findById(categoryId).orElseThrow(EntityNotFoundException::new);
    String code = normalizeCategory(request.getCode());
    categoryRepository.findByCode(code).ifPresent(existing -> {
      if (!existing.getId().equals(categoryId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别编码已存在");
      }
    });
    category.setCode(code);
    category.setName(request.getName().trim());
    category.setDescription(normalizeOptional(request.getDescription()));
    category.setStatus(defaultIfBlank(request.getStatus(), "ACTIVE"));
    category.setSortOrder(request.getSortOrder() == null ? 100 : request.getSortOrder());
    return toCategoryResponse(categoryRepository.save(category));
  }

  @Transactional
  public void deleteCategory(Long categoryId) {
    DomainCategory category = categoryRepository.findById(categoryId).orElseThrow(EntityNotFoundException::new);
    boolean usedInGlobal = entryRepository.existsByCategoryRefId(categoryId)
        || entryRepository.existsByCategoryIgnoreCase(category.getCode());
    boolean usedInLocal = kbLexiconRepository.existsByCategoryRefId(categoryId)
        || kbLexiconRepository.existsByCategoryIgnoreCase(category.getCode());
    if (usedInGlobal || usedInLocal) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别已被词条使用，无法删除");
    }
    categoryRelationRepository.deleteBySourceCategoryRefIdOrTargetCategoryRefId(categoryId, categoryId);
    categoryRepository.delete(category);
  }

  // ===== 类别关联关系管理 =====
  public List<DomainCategoryRelationResponse> listCategoryRelations(boolean activeOnly) {
    List<DomainCategoryRelation> rows = activeOnly
        ? categoryRelationRepository.findByEnabledTrueOrderBySourceCategoryRefIdAscTargetCategoryRefIdAscIdAsc()
        : categoryRelationRepository.findAllByOrderBySourceCategoryRefIdAscTargetCategoryRefIdAscIdAsc();

    return rows.stream().map(this::toCategoryRelationResponse).toList();
  }

  @Transactional
  public DomainCategoryRelationResponse createCategoryRelation(DomainCategoryRelationRequest request) {
    DomainCategory sourceRef = findCategoryById(request.getSourceCategoryId());
    DomainCategory targetRef = findCategoryById(request.getTargetCategoryId());
    String source = sourceRef.getCode();
    String target = targetRef.getCode();
    String label = normalizeTerm(request.getRelationLabel());
    categoryRelationRepository.findBySourceCategoryRefIdAndTargetCategoryRefIdAndRelationLabel(
        sourceRef.getId(), targetRef.getId(), label)
        .ifPresent(existing -> {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别关联已存在");
        });
    DomainCategoryRelation relation = DomainCategoryRelation.builder()
        .sourceCategoryRef(sourceRef)
        .targetCategoryRef(targetRef)
        .sourceCategory(source)
        .targetCategory(target)
        .relationLabel(label)
        .enabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled())
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    return toCategoryRelationResponse(categoryRelationRepository.save(relation));
  }

  @Transactional
  public DomainCategoryRelationResponse updateCategoryRelation(Long relationId, DomainCategoryRelationRequest request) {
    DomainCategoryRelation relation = categoryRelationRepository.findById(relationId)
        .orElseThrow(EntityNotFoundException::new);
    DomainCategory sourceRef = findCategoryById(request.getSourceCategoryId());
    DomainCategory targetRef = findCategoryById(request.getTargetCategoryId());
    String source = sourceRef.getCode();
    String target = targetRef.getCode();
    String label = normalizeTerm(request.getRelationLabel());
    categoryRelationRepository.findBySourceCategoryRefIdAndTargetCategoryRefIdAndRelationLabel(
        sourceRef.getId(), targetRef.getId(), label)
        .ifPresent(existing -> {
          if (!existing.getId().equals(relationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别关联已存在");
          }
        });
    relation.setSourceCategoryRef(sourceRef);
    relation.setTargetCategoryRef(targetRef);
    relation.setSourceCategory(source);
    relation.setTargetCategory(target);
    relation.setRelationLabel(label);
    relation.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
    return toCategoryRelationResponse(categoryRelationRepository.save(relation));
  }

  @Transactional
  public void deleteCategoryRelation(Long relationId) {
    DomainCategoryRelation relation = categoryRelationRepository.findById(relationId)
        .orElseThrow(EntityNotFoundException::new);
    categoryRelationRepository.delete(relation);
  }

  public Map<String, Map<String, String>> buildCategoryRelationLabelMap() {
    Map<String, Map<String, String>> map = new LinkedHashMap<>();
    for (DomainCategoryRelation relation : categoryRelationRepository
        .findByEnabledTrueOrderBySourceCategoryRefIdAscTargetCategoryRefIdAscIdAsc()) {
      String sourceCode = relation.getSourceCategoryRef() == null
          ? relation.getSourceCategory()
          : relation.getSourceCategoryRef().getCode();
      String targetCode = relation.getTargetCategoryRef() == null
          ? relation.getTargetCategory()
          : relation.getTargetCategoryRef().getCode();
      String source = normalizeCategory(sourceCode);
      String target = normalizeCategory(targetCode);
      if (source.isBlank() || target.isBlank()) {
        continue;
      }
      map.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(target, relation.getRelationLabel());
    }
    return map;
  }

  // ===== 新接口：全局词典包 =====
  public List<DictionaryPackResponse> listPacks() {
    return packRepository.findAll().stream()
        .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
        .map(this::toPackResponse)
        .toList();
  }

  @Transactional
  public DictionaryPackResponse createPack(DictionaryPackRequest request) {
    String code = request.getCode().trim().toUpperCase(Locale.ROOT);
    packRepository.findByCode(code).ifPresent(existing -> {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "词典包编码已存在");
    });
    DomainDictionaryPack pack = DomainDictionaryPack.builder()
        .code(code)
        .name(request.getName().trim())
        .scopeType(defaultIfBlank(request.getScopeType(), "GLOBAL"))
        .status(defaultIfBlank(request.getStatus(), "ACTIVE"))
        .description(normalizeOptional(request.getDescription()))
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    return toPackResponse(packRepository.save(pack));
  }

  @Transactional
  public DictionaryPackResponse updatePack(Long packId, DictionaryPackRequest request) {
    DomainDictionaryPack pack = packRepository.findById(packId).orElseThrow(EntityNotFoundException::new);
    String code = request.getCode().trim().toUpperCase(Locale.ROOT);
    packRepository.findByCode(code).ifPresent(existing -> {
      if (!existing.getId().equals(packId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "词典包编码已存在");
      }
    });
    pack.setCode(code);
    pack.setName(request.getName().trim());
    pack.setScopeType(defaultIfBlank(request.getScopeType(), "GLOBAL"));
    pack.setStatus(defaultIfBlank(request.getStatus(), "ACTIVE"));
    pack.setDescription(normalizeOptional(request.getDescription()));
    return toPackResponse(packRepository.save(pack));
  }

  @Transactional
  public void deletePack(Long packId) {
    DomainDictionaryPack pack = packRepository.findById(packId).orElseThrow(EntityNotFoundException::new);
    kbPackRepository.deleteByPackId(packId);
    entryRepository.deleteByPackId(packId);
    packRepository.delete(pack);
  }

  public List<DictionaryEntryResponse> listPackEntries(Long packId) {
    packRepository.findById(packId).orElseThrow(EntityNotFoundException::new);
    return entryRepository.findByPackIdOrderByCategoryAscTermAsc(packId).stream().map(this::toEntryResponse).toList();
  }

  @Transactional
  public DictionaryEntryResponse upsertPackEntry(Long packId, DictionaryEntryRequest request, String sourceType) {
    packRepository.findById(packId).orElseThrow(EntityNotFoundException::new);
    DomainCategory categoryRef = resolveCategory(request.getCategoryId(), request.getCategory());
    String category = categoryRef.getCode();
    String term = normalizeTerm(request.getTerm());

    DomainDictionaryEntry entry = entryRepository.findByPackIdAndCategoryRefIdAndTerm(packId, categoryRef.getId(), term)
        .orElseGet(() -> DomainDictionaryEntry.builder()
            .packId(packId)
            .categoryRef(categoryRef)
            .category(category)
            .term(term)
            .enabled(Boolean.TRUE)
            .sourceType(defaultIfBlank(sourceType, "MANUAL"))
            .createdBy(currentUserService.getCurrentUserId())
            .build());

    entry.setStandardTerm(normalizeOptional(request.getStandardTerm()));
    entry.setEnabled(Optional.ofNullable(request.getEnabled()).orElse(Boolean.TRUE));
    if (entry.getSourceType() == null || entry.getSourceType().isBlank()) {
      entry.setSourceType(defaultIfBlank(sourceType, "MANUAL"));
    }
    return toEntryResponse(entryRepository.save(entry));
  }

  @Transactional
  public DictionaryEntryResponse updatePackEntry(Long entryId, DictionaryEntryRequest request) {
    DomainDictionaryEntry entry = entryRepository.findById(entryId).orElseThrow(EntityNotFoundException::new);
    DomainCategory categoryRef = resolveCategory(request.getCategoryId(), request.getCategory());
    String category = categoryRef.getCode();
    String term = normalizeTerm(request.getTerm());
    entryRepository.findByPackIdAndCategoryRefIdAndTerm(entry.getPackId(), categoryRef.getId(), term)
        .ifPresent(existing -> {
          if (!existing.getId().equals(entryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "词条已存在");
          }
        });
    entry.setCategoryRef(categoryRef);
    entry.setCategory(category);
    entry.setTerm(term);
    entry.setStandardTerm(normalizeOptional(request.getStandardTerm()));
    entry.setEnabled(Optional.ofNullable(request.getEnabled()).orElse(Boolean.TRUE));
    return toEntryResponse(entryRepository.save(entry));
  }

  @Transactional
  public void deletePackEntry(Long entryId) {
    DomainDictionaryEntry entry = entryRepository.findById(entryId).orElseThrow(EntityNotFoundException::new);
    entryRepository.delete(entry);
  }

  @Transactional
  public DictionaryBatchUpsertResponse batchUpsertPackEntries(Long packId, DictionaryBatchUpsertRequest request) {
    String format = defaultIfBlank(request.getFormat(), "TEXT").toUpperCase(Locale.ROOT);
    List<DictionaryEntryRequest> rows = "JSON".equals(format)
        ? parseJson(request.getContent())
        : parseStructuredText(request.getContent());

    int success = 0;
    List<String> errors = new ArrayList<>();
    int lineNo = 0;
    for (DictionaryEntryRequest row : rows) {
      lineNo++;
      try {
        upsertPackEntry(packId, row, "BATCH");
        success++;
      } catch (Exception ex) {
        errors.add("第" + lineNo + "条失败: " + ex.getMessage());
      }
    }

    return DictionaryBatchUpsertResponse.builder()
        .total(rows.size())
        .success(success)
        .failed(rows.size() - success)
        .errors(errors)
        .build();
  }

  public List<KnowledgeBaseDictionaryPackResponse> listKnowledgeBasePacks(Long knowledgeBaseId) {
    baseRepository.findById(knowledgeBaseId).orElseThrow(EntityNotFoundException::new);
    Map<Long, DomainDictionaryPack> packMap = packRepository.findAll().stream()
        .collect(Collectors.toMap(DomainDictionaryPack::getId, p -> p));
    return kbPackRepository.findByKnowledgeBaseIdOrderByPriorityDescIdAsc(knowledgeBaseId).stream()
        .map(binding -> {
          DomainDictionaryPack pack = packMap.get(binding.getPackId());
          return KnowledgeBaseDictionaryPackResponse.builder()
              .id(binding.getId())
              .knowledgeBaseId(binding.getKnowledgeBaseId())
              .packId(binding.getPackId())
              .packName(pack == null ? "-" : pack.getName())
              .packCode(pack == null ? "-" : pack.getCode())
              .priority(binding.getPriority())
              .enabled(binding.getEnabled())
              .build();
        })
        .toList();
  }

  @Transactional
  public KnowledgeBaseDictionaryPackResponse bindPackToKnowledgeBase(
      Long knowledgeBaseId,
      Long packId,
      KnowledgeBaseDictionaryPackBindRequest request) {
    baseRepository.findById(knowledgeBaseId).orElseThrow(EntityNotFoundException::new);
    DomainDictionaryPack pack = packRepository.findById(packId).orElseThrow(EntityNotFoundException::new);

    KnowledgeBaseDictionaryPack relation = kbPackRepository
        .findByKnowledgeBaseIdAndPackId(knowledgeBaseId, packId)
        .orElseGet(() -> KnowledgeBaseDictionaryPack.builder()
            .knowledgeBaseId(knowledgeBaseId)
            .packId(packId)
            .priority(100)
            .enabled(Boolean.TRUE)
            .createdBy(currentUserService.getCurrentUserId())
            .build());

    relation.setPriority(Optional.ofNullable(request.getPriority()).orElse(100));
    relation.setEnabled(Optional.ofNullable(request.getEnabled()).orElse(Boolean.TRUE));
    relation = kbPackRepository.save(relation);

    return KnowledgeBaseDictionaryPackResponse.builder()
        .id(relation.getId())
        .knowledgeBaseId(relation.getKnowledgeBaseId())
        .packId(relation.getPackId())
        .packName(pack.getName())
        .packCode(pack.getCode())
        .priority(relation.getPriority())
        .enabled(relation.getEnabled())
        .build();
  }

  @Transactional
  public void unbindPackFromKnowledgeBase(Long knowledgeBaseId, Long packId) {
    baseRepository.findById(knowledgeBaseId).orElseThrow(EntityNotFoundException::new);
    kbPackRepository.deleteByKnowledgeBaseIdAndPackId(knowledgeBaseId, packId);
  }

  public Map<String, Map<String, String>> buildEffectiveLexiconAliasMap(Long knowledgeBaseId) {
    Map<String, Map<String, String>> result = new LinkedHashMap<>();

    // 1) 先加载激活词典包（按优先级）
    List<KnowledgeBaseDictionaryPack> bindings = kbPackRepository
        .findByKnowledgeBaseIdOrderByPriorityDescIdAsc(knowledgeBaseId)
        .stream().filter(KnowledgeBaseDictionaryPack::getEnabled).toList();
    List<Long> packIds = bindings.stream().map(KnowledgeBaseDictionaryPack::getPackId).toList();
    for (DomainDictionaryEntry entry : entryRepository.findByPackIdInAndEnabledTrue(packIds)) {
      String category = entry.getCategoryRef() == null ? entry.getCategory() : entry.getCategoryRef().getCode();
      mergeAlias(result, category, entry.getTerm(), entry.getStandardTerm());
    }

    // 2) 再加载知识库本地词条（作为补充覆盖）
    for (KnowledgeBaseDomainLexicon entry : kbLexiconRepository.findByKnowledgeBaseIdAndEnabledTrue(knowledgeBaseId)) {
      String category = entry.getCategoryRef() == null ? entry.getCategory() : entry.getCategoryRef().getCode();
      mergeAlias(result, category, entry.getTerm(), entry.getStandardTerm());
    }
    return result;
  }

  private List<DictionaryEntryRequest> parseJson(String raw) {
    try {
      List<Map<String, Object>> rows = objectMapper.readValue(raw, new TypeReference<>() {
      });
      List<DictionaryEntryRequest> result = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        DictionaryEntryRequest req = new DictionaryEntryRequest();
        req.setCategory(Objects.toString(row.get("category"), ""));
        req.setTerm(Objects.toString(row.get("term"), ""));
        req.setStandardTerm(row.get("standardTerm") == null ? null : Objects.toString(row.get("standardTerm"), null));
        req.setEnabled(
            row.get("enabled") == null ? Boolean.TRUE : Boolean.valueOf(Objects.toString(row.get("enabled"))));
        result.add(req);
      }
      return result;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 解析失败: " + ex.getMessage());
    }
  }

  private List<DictionaryEntryRequest> parseStructuredText(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<DictionaryEntryRequest> result = new ArrayList<>();
    String currentCategory = "GENERAL";
    for (String line : raw.split("\\r?\\n")) {
      String value = line == null ? "" : line.trim();
      if (value.isBlank() || value.startsWith("#")) {
        continue;
      }
      if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
        currentCategory = value.substring(1, value.length() - 1).trim();
        continue;
      }

      String[] cells = value.split(",", -1);
      if (cells.length == 0 || cells[0].trim().isBlank()) {
        continue;
      }
      DictionaryEntryRequest req = new DictionaryEntryRequest();
      req.setCategory(currentCategory);
      req.setTerm(cells[0].trim());
      if (cells.length > 1 && !cells[1].trim().isBlank()) {
        req.setStandardTerm(cells[1].trim());
      }
      req.setEnabled(true);
      result.add(req);
    }
    return result;
  }

  private void mergeAlias(Map<String, Map<String, String>> target, String category, String term, String standardTerm) {
    String c = normalizeCategory(category);
    if (categoryRepository.findByCode(c).isEmpty()) {
      return;
    }
    String t = normalizeTerm(term);
    String standard = normalizeOptional(standardTerm);
    String canonical = standard == null ? t : standard;
    target.computeIfAbsent(c, key -> new LinkedHashMap<>()).put(t, canonical);
    target.get(c).put(canonical, canonical);
  }

  private String normalizeCategory(String category) {
    if (category == null || category.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category不能为空");
    }
    return category.trim().toUpperCase(Locale.ROOT);
  }

  private DomainCategory findCategoryByCode(String code) {
    return categoryRepository.findByCode(code)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别不存在: " + code));
  }

  private DomainCategory findCategoryById(Long id) {
    if (id == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId不能为空");
    }
    return categoryRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "类别不存在: " + id));
  }

  private DomainCategory resolveCategory(Long categoryId, String categoryCode) {
    if (categoryId != null) {
      return findCategoryById(categoryId);
    }
    if (categoryCode != null && !categoryCode.isBlank()) {
      return findCategoryByCode(normalizeCategory(categoryCode));
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId不能为空");
  }

  private String normalizeTerm(String term) {
    if (term == null || term.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "term不能为空");
    }
    return term.trim();
  }

  private String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }

  private String defaultIfBlank(String value, String defaultValue) {
    String trimmed = normalizeOptional(value);
    return trimmed == null ? defaultValue : trimmed;
  }

  private DictionaryPackResponse toPackResponse(DomainDictionaryPack pack) {
    return DictionaryPackResponse.builder()
        .id(pack.getId())
        .code(pack.getCode())
        .name(pack.getName())
        .scopeType(pack.getScopeType())
        .status(pack.getStatus())
        .description(pack.getDescription())
        .createdAt(pack.getCreatedAt())
        .build();
  }

  private DomainCategoryResponse toCategoryResponse(DomainCategory category) {
    return DomainCategoryResponse.builder()
        .id(category.getId())
        .code(category.getCode())
        .name(category.getName())
        .description(category.getDescription())
        .status(category.getStatus())
        .sortOrder(category.getSortOrder())
        .createdAt(category.getCreatedAt())
        .build();
  }

  private DomainCategoryRelationResponse toCategoryRelationResponse(DomainCategoryRelation relation) {
    DomainCategory source = relation.getSourceCategoryRef();
    DomainCategory target = relation.getTargetCategoryRef();
    return DomainCategoryRelationResponse.builder()
        .id(relation.getId())
        .sourceCategoryId(source == null ? null : source.getId())
        .sourceCategory(source == null ? relation.getSourceCategory() : source.getCode())
        .sourceCategoryName(source == null ? null : source.getName())
        .targetCategoryId(target == null ? null : target.getId())
        .targetCategory(target == null ? relation.getTargetCategory() : target.getCode())
        .targetCategoryName(target == null ? null : target.getName())
        .relationLabel(relation.getRelationLabel())
        .enabled(relation.getEnabled())
        .createdAt(relation.getCreatedAt())
        .build();
  }

  private DictionaryEntryResponse toEntryResponse(DomainDictionaryEntry entry) {
    DomainCategory c = entry.getCategoryRef();
    return DictionaryEntryResponse.builder()
        .id(entry.getId())
        .packId(entry.getPackId())
        .categoryId(c == null ? null : c.getId())
        .category(c == null ? entry.getCategory() : c.getCode())
        .categoryName(c == null ? null : c.getName())
        .term(entry.getTerm())
        .standardTerm(entry.getStandardTerm())
        .enabled(entry.getEnabled())
        .sourceType(entry.getSourceType())
        .createdAt(entry.getCreatedAt())
        .build();
  }

  private KnowledgeLexiconResponse toKbLexiconResponse(KnowledgeBaseDomainLexicon lexicon) {
    DomainCategory c = lexicon.getCategoryRef();
    return KnowledgeLexiconResponse.builder()
        .id(lexicon.getId())
        .knowledgeBaseId(lexicon.getKnowledgeBase().getId())
        .categoryId(c == null ? null : c.getId())
        .category(c == null ? lexicon.getCategory() : c.getCode())
        .categoryName(c == null ? null : c.getName())
        .term(lexicon.getTerm())
        .standardTerm(lexicon.getStandardTerm())
        .enabled(lexicon.getEnabled())
        .createdAt(lexicon.getCreatedAt())
        .build();
  }
}
