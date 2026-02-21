package com.bidcollab.service;

import com.bidcollab.dto.DictionaryBatchUpsertRequest;
import com.bidcollab.dto.DictionaryBatchUpsertResponse;
import com.bidcollab.dto.DictionaryEntryRequest;
import com.bidcollab.dto.DictionaryEntryResponse;
import com.bidcollab.dto.DictionaryPackRequest;
import com.bidcollab.dto.DictionaryPackResponse;
import com.bidcollab.dto.DomainLexiconUpsertRequest;
import com.bidcollab.dto.DomainLexiconUpdateRequest;
import com.bidcollab.dto.KnowledgeBaseDictionaryPackBindRequest;
import com.bidcollab.dto.KnowledgeBaseDictionaryPackResponse;
import com.bidcollab.dto.KnowledgeLexiconResponse;
import com.bidcollab.entity.DomainDictionaryEntry;
import com.bidcollab.entity.DomainDictionaryPack;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeBaseDictionaryPack;
import com.bidcollab.entity.KnowledgeBaseDomainLexicon;
import com.bidcollab.repository.DomainDictionaryEntryRepository;
import com.bidcollab.repository.DomainDictionaryPackRepository;
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
  private final KnowledgeBaseDictionaryPackRepository kbPackRepository;
  private final ObjectMapper objectMapper;

  public DomainLexiconService(
      KnowledgeBaseDomainLexiconRepository kbLexiconRepository,
      KnowledgeBaseRepository baseRepository,
      CurrentUserService currentUserService,
      DomainDictionaryPackRepository packRepository,
      DomainDictionaryEntryRepository entryRepository,
      KnowledgeBaseDictionaryPackRepository kbPackRepository,
      ObjectMapper objectMapper) {
    this.kbLexiconRepository = kbLexiconRepository;
    this.baseRepository = baseRepository;
    this.currentUserService = currentUserService;
    this.packRepository = packRepository;
    this.entryRepository = entryRepository;
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
    String category = normalizeCategory(request.getCategory());
    String term = normalizeTerm(request.getTerm());
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository
        .findByKnowledgeBaseIdAndCategoryAndTerm(kb.getId(), category, term)
        .orElseGet(() -> KnowledgeBaseDomainLexicon.builder()
            .knowledgeBase(kb)
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
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository.findById(lexiconId).orElseThrow(EntityNotFoundException::new);
    if (!lexicon.getKnowledgeBase().getId().equals(request.getKnowledgeBaseId())) {
      throw new IllegalArgumentException("Lexicon does not belong to knowledge base");
    }
    String category = normalizeCategory(request.getCategory());
    String term = normalizeTerm(request.getTerm());
    kbLexiconRepository.findByKnowledgeBaseIdAndCategoryAndTerm(request.getKnowledgeBaseId(), category, term)
        .ifPresent(existing -> {
          if (!existing.getId().equals(lexiconId)) {
            throw new IllegalArgumentException("Lexicon term already exists in this category");
          }
        });
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
    KnowledgeBaseDomainLexicon lexicon = kbLexiconRepository.findById(lexiconId).orElseThrow(EntityNotFoundException::new);
    kbLexiconRepository.delete(lexicon);
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
    String category = normalizeCategory(request.getCategory());
    String term = normalizeTerm(request.getTerm());

    DomainDictionaryEntry entry = entryRepository.findByPackIdAndCategoryAndTerm(packId, category, term)
        .orElseGet(() -> DomainDictionaryEntry.builder()
            .packId(packId)
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
    String category = normalizeCategory(request.getCategory());
    String term = normalizeTerm(request.getTerm());
    entryRepository.findByPackIdAndCategoryAndTerm(entry.getPackId(), category, term)
        .ifPresent(existing -> {
          if (!existing.getId().equals(entryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "词条已存在");
          }
        });
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
    List<KnowledgeBaseDictionaryPack> bindings = kbPackRepository.findByKnowledgeBaseIdOrderByPriorityDescIdAsc(knowledgeBaseId)
        .stream().filter(KnowledgeBaseDictionaryPack::getEnabled).toList();
    List<Long> packIds = bindings.stream().map(KnowledgeBaseDictionaryPack::getPackId).toList();
    for (DomainDictionaryEntry entry : entryRepository.findByPackIdInAndEnabledTrue(packIds)) {
      mergeAlias(result, entry.getCategory(), entry.getTerm(), entry.getStandardTerm());
    }

    // 2) 再加载知识库本地词条（作为补充覆盖）
    for (KnowledgeBaseDomainLexicon entry : kbLexiconRepository.findByKnowledgeBaseIdAndEnabledTrue(knowledgeBaseId)) {
      mergeAlias(result, entry.getCategory(), entry.getTerm(), entry.getStandardTerm());
    }
    return result;
  }

  private List<DictionaryEntryRequest> parseJson(String raw) {
    try {
      List<Map<String, Object>> rows = objectMapper.readValue(raw, new TypeReference<>() {});
      List<DictionaryEntryRequest> result = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        DictionaryEntryRequest req = new DictionaryEntryRequest();
        req.setCategory(Objects.toString(row.get("category"), ""));
        req.setTerm(Objects.toString(row.get("term"), ""));
        req.setStandardTerm(row.get("standardTerm") == null ? null : Objects.toString(row.get("standardTerm"), null));
        req.setEnabled(row.get("enabled") == null ? Boolean.TRUE : Boolean.valueOf(Objects.toString(row.get("enabled"))));
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

  private DictionaryEntryResponse toEntryResponse(DomainDictionaryEntry entry) {
    return DictionaryEntryResponse.builder()
        .id(entry.getId())
        .packId(entry.getPackId())
        .category(entry.getCategory())
        .term(entry.getTerm())
        .standardTerm(entry.getStandardTerm())
        .enabled(entry.getEnabled())
        .sourceType(entry.getSourceType())
        .createdAt(entry.getCreatedAt())
        .build();
  }

  private KnowledgeLexiconResponse toKbLexiconResponse(KnowledgeBaseDomainLexicon lexicon) {
    return KnowledgeLexiconResponse.builder()
        .id(lexicon.getId())
        .knowledgeBaseId(lexicon.getKnowledgeBase().getId())
        .category(lexicon.getCategory())
        .term(lexicon.getTerm())
        .standardTerm(lexicon.getStandardTerm())
        .enabled(lexicon.getEnabled())
        .createdAt(lexicon.getCreatedAt())
        .build();
  }
}
