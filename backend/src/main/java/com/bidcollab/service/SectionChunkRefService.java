package com.bidcollab.service;

import com.bidcollab.dto.SectionChunkRefResponse;
import com.bidcollab.dto.KnowledgeSearchResult;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionChunkRef;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.repository.SectionChunkRefRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionChunkRefService {
  private static final Pattern CHUNK_MARKER = Pattern.compile("\\[chunk#(\\d+)]", Pattern.CASE_INSENSITIVE);
  private static final int MAX_QUOTE_LEN = 1200;

  private final SectionChunkRefRepository sectionChunkRefRepository;

  public SectionChunkRefService(SectionChunkRefRepository sectionChunkRefRepository) {
    this.sectionChunkRefRepository = sectionChunkRefRepository;
  }

  @Transactional
  public void rebuildRefs(Section section, SectionVersion version, String content, Long userId) {
    sectionChunkRefRepository.deleteBySectionId(section.getId());
    List<ExtractedRef> extracted = extractRefs(content);
    if (extracted.isEmpty()) {
      return;
    }
    List<SectionChunkRef> refs = new ArrayList<>(extracted.size());
    for (ExtractedRef item : extracted) {
      refs.add(SectionChunkRef.builder()
          .section(section)
          .sectionVersion(version)
          .paragraphIndex(item.paragraphIndex())
          .chunkId(item.chunkId())
          .quoteText(item.quoteText())
          .createdBy(userId)
          .build());
    }
    sectionChunkRefRepository.saveAll(refs);
  }

  @Transactional
  public void replaceByHits(Section section, SectionVersion version, List<KnowledgeSearchResult> hits, Long userId) {
    sectionChunkRefRepository.deleteBySectionId(section.getId());
    if (hits == null || hits.isEmpty()) {
      return;
    }
    List<SectionChunkRef> refs = new ArrayList<>();
    Set<Long> unique = new LinkedHashSet<>();
    int rank = 1;
    for (KnowledgeSearchResult hit : hits) {
      if (hit == null || hit.getChunkId() == null || !unique.add(hit.getChunkId())) {
        continue;
      }
      String quote = normalizeText(hit.getContent());
      if (quote.length() > MAX_QUOTE_LEN) {
        quote = quote.substring(0, MAX_QUOTE_LEN);
      }
      refs.add(SectionChunkRef.builder()
          .section(section)
          .sectionVersion(version)
          .paragraphIndex(rank++)
          .chunkId(hit.getChunkId())
          .quoteText(quote)
          .createdBy(userId)
          .build());
      if (refs.size() >= 12) {
        break;
      }
    }
    if (!refs.isEmpty()) {
      sectionChunkRefRepository.saveAll(refs);
    }
  }

  @Transactional
  public void deleteBySectionId(Long sectionId) {
    sectionChunkRefRepository.deleteBySectionId(sectionId);
  }

  @Transactional
  public void deleteBySectionIds(List<Long> sectionIds) {
    if (sectionIds == null || sectionIds.isEmpty()) {
      return;
    }
    sectionChunkRefRepository.deleteBySectionIdIn(sectionIds);
  }

  @Transactional(readOnly = true)
  public List<SectionChunkRefResponse> listBySection(Long sectionId) {
    return sectionChunkRefRepository.findBySectionIdOrderByParagraphIndexAscIdAsc(sectionId).stream()
        .map(ref -> SectionChunkRefResponse.builder()
            .id(ref.getId())
            .sectionId(ref.getSection().getId())
            .sectionVersionId(ref.getSectionVersion() == null ? null : ref.getSectionVersion().getId())
            .paragraphIndex(ref.getParagraphIndex())
            .chunkId(ref.getChunkId())
            .quoteText(ref.getQuoteText())
            .createdAt(ref.getCreatedAt())
            .build())
        .toList();
  }

  private List<ExtractedRef> extractRefs(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    List<String> blocks = extractBlocks(content);
    List<ExtractedRef> refs = new ArrayList<>();
    for (int i = 0; i < blocks.size(); i++) {
      String text = normalizeText(blocks.get(i));
      if (text.isBlank()) {
        continue;
      }
      Matcher matcher = CHUNK_MARKER.matcher(text);
      Set<Long> chunkIds = new LinkedHashSet<>();
      while (matcher.find()) {
        try {
          chunkIds.add(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
        }
      }
      if (chunkIds.isEmpty()) {
        continue;
      }
      String quote = normalizeText(CHUNK_MARKER.matcher(text).replaceAll(""));
      if (quote.length() > MAX_QUOTE_LEN) {
        quote = quote.substring(0, MAX_QUOTE_LEN);
      }
      int paragraphIndex = i + 1;
      for (Long chunkId : chunkIds) {
        refs.add(new ExtractedRef(paragraphIndex, chunkId, quote));
      }
    }
    return refs;
  }

  private List<String> extractBlocks(String content) {
    if (!looksLikeHtml(content)) {
      return List.of(content.split("\\R+"));
    }
    Document doc = Jsoup.parseBodyFragment(content);
    List<Element> elements = doc.body().select("p,li,td,th,h1,h2,h3,h4,h5,h6,blockquote");
    if (elements.isEmpty()) {
      String text = doc.body().text();
      return text.isBlank() ? List.of() : List.of(text);
    }
    return elements.stream().map(Element::text).toList();
  }

  private boolean looksLikeHtml(String content) {
    return content != null && content.matches("(?s).*</?[a-zA-Z][^>]*>.*");
  }

  private String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
  }

  private record ExtractedRef(int paragraphIndex, long chunkId, String quoteText) {
  }
}
