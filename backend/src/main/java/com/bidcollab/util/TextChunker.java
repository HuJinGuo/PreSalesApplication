package com.bidcollab.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
  // [AI-READ] 文本切分策略：优先章节切分，保障语义完整性。
  private static final Pattern HEADING_PATTERN = Pattern.compile(
      "^(#{1,6}\\s+.+|第[一二三四五六七八九十百0-9]+[章节部分篇].*|(?:[0-9]+(?:\\.[0-9]+){1,4}|[0-9]+[、.)])\\s+.+)$");

  /**
   * 基础分块方法：按固定长度切分文本，并保留重叠部分。
   * <p>
   * 适用于无明显结构或纯文本内容的简单切分。
   *
   * @param text      待切分的原始文本
   * @param chunkSize 每个分块的最大字符数
   * @param overlap   相邻分块之间的重叠字符数，用于保持上下文连贯性
   * @return 切分后的文本块列表
   * @throws IllegalArgumentException 如果 chunkSize 小于或等于 overlap
   */
  public List<String> split(String text, int chunkSize, int overlap) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    if (chunkSize <= overlap) {
      throw new IllegalArgumentException("chunkSize must be greater than overlap");
    }
    // 统一换行符并去除首尾空白
    String normalized = text.replace("\r\n", "\n").trim();
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < normalized.length()) {
      int end = Math.min(start + chunkSize, normalized.length());
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) {
        break;
      }
      // 回退 overlap 长度，确保下一块包含重叠内容
      start = end - overlap;
    }
    return chunks;
  }

  /**
   * 智能分块方法：针对技术文档进行结构化切分。
   * <p>
   * 策略：
   * 1. 优先按章节标题（Markdown标题、"第X章"、数字编号等）进行一级切分，保持章节内部语义完整。
   * 2. 如果章节内容过长，再在章节内部按固定长度进行二级切分，同时保留重叠上下文。
   * 3. 会自动添加 "[SECTION] 标题" 前缀，增强语义检索效果。
   *
   * @param text      待切分的原始文本
   * @param chunkSize 每个分块的目标最大字符数
   * @param overlap   二级切分时的重叠字符数
   * @return 切分后的文本块列表
   */
  public List<String> splitBySections(String text, int chunkSize, int overlap) {
    return splitBySectionsWithMetadata(text, chunkSize, overlap).stream().map(StructuredChunk::getContent).toList();
  }

  /**
   * 结构化分块：
   * 1) chunk 内容仅包含“最后一级标题 + 正文”；
   * 2) sectionPath 记录完整路径（用于后续元数据过滤/加权）；
   * 3) 对“无正文但有子章节”的父标题生成 ANCHOR chunk，避免父标题语义丢失。
   */
  public List<StructuredChunk> splitBySectionsWithMetadata(String text, int chunkSize, int overlap) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    if (chunkSize <= overlap) {
      throw new IllegalArgumentException("chunkSize must be greater than overlap");
    }

    List<SectionBlock> sections = parseSections(text);
    Map<String, List<String>> childrenByParentPath = buildChildrenMap(sections);
    List<StructuredChunk> result = new ArrayList<>();

    for (SectionBlock section : sections) {
      // 归一化章节内容：统一换行符，压缩多余空行
      String normalized = section.content.replace("\r\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
      if (normalized.isBlank()) {
        List<String> children = childrenByParentPath.getOrDefault(section.path, List.of());
        if (!children.isEmpty()) {
          result.add(StructuredChunk.builder()
              .content("[SECTION] " + section.title + "\n子章节：" + String.join("、", children))
              .sectionTitle(section.title)
              .sectionPath(section.path)
              .chunkType("ANCHOR")
              .build());
        }
        continue;
      }

      // 构造当前章节的上下文前缀，例如 "[SECTION] 第一章 引言\n"
      // 注意：仅使用最后一级标题，不再拼接父级标题，避免语义污染。
      String sectionPrefix = section.title.isBlank() ? "" : ("[SECTION] " + section.title + "\n");
      // 计算实际可用的内容长度（扣除前缀长度）
      // 这里的 180 是一个保底长度，防止前缀过长导致内容太短
      int payloadMax = Math.max(180, chunkSize - sectionPrefix.length());

      // 第二步：章节内部按长度切分
      int start = 0;
      while (start < normalized.length()) {
        int end = Math.min(start + payloadMax, normalized.length());
        String payload = normalized.substring(start, end).trim();
        if (!payload.isBlank()) {
          result.add(StructuredChunk.builder()
              .content(sectionPrefix + payload)
              .sectionTitle(section.title)
              .sectionPath(section.path)
              .chunkType("TEXT")
              .build());
        }
        if (end == normalized.length()) {
          break;
        }
        // 重叠回退，注意不要回退到负数
        start = Math.max(0, end - overlap);
      }
    }
    return result;
  }

  /**
   * 解析文本中的章节结构。
   * 
   * @param text 原始文本
   * @return 解析出的章节列表
   */
  private List<SectionBlock> parseSections(String text) {
    List<SectionBlock> sections = new ArrayList<>();
    String currentTitle = "";
    String currentPath = "";
    int currentLevel = 1;
    StringBuilder currentBody = new StringBuilder();
    List<String> headingStack = new ArrayList<>();

    // 逐行扫描
    for (String rawLine : text.replace("\r\n", "\n").split("\n")) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isBlank()) {
        if (!currentBody.isEmpty()) {
          currentBody.append('\n'); // 保留段落间的空行结构
        }
        continue;
      }

      // 如果发现新标题，则将之前的章节内容（如有）保存
      if (isHeadingLine(line)) {
        flushSection(sections, currentTitle, currentPath, currentLevel, currentBody);
        // 提取纯标题文本（去除 # 等标记）
        currentTitle = stripHeadingMarker(line);
        currentLevel = detectHeadingLevel(line);
        while (headingStack.size() >= currentLevel) {
          headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(currentTitle);
        currentPath = String.join(" > ", headingStack);
        currentBody = new StringBuilder();
      } else {
        // 普通文本行追加到当前章节内容
        currentBody.append(line).append('\n');
      }
    }
    // 循环结束后，保存最后一个章节的内容
    flushSection(sections, currentTitle, currentPath, currentLevel, currentBody);
    return sections;
  }

  /**
   * 将当前累积的内容保存为一个 SectionBlock。
   */
  private void flushSection(List<SectionBlock> sections, String title, String path, int level, StringBuilder body) {
    String normalizedTitle = title == null ? "" : title.trim();
    String normalizedPath = path == null ? "" : path.trim();
    String content = body.toString().trim();
    if (!content.isBlank() || !normalizedTitle.isBlank()) {
      sections.add(new SectionBlock(normalizedTitle, normalizedPath, level, content));
    }
  }

  private Map<String, List<String>> buildChildrenMap(List<SectionBlock> sections) {
    Map<String, List<String>> map = new HashMap<>();
    for (SectionBlock section : sections) {
      String parentPath = parentPath(section.path);
      if (parentPath.isBlank() || section.title.isBlank()) {
        continue;
      }
      map.computeIfAbsent(parentPath, k -> new ArrayList<>());
      List<String> children = map.get(parentPath);
      if (!children.contains(section.title)) {
        children.add(section.title);
      }
    }
    return map;
  }

  private String parentPath(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    int idx = path.lastIndexOf(" > ");
    if (idx < 0) {
      return "";
    }
    return path.substring(0, idx);
  }

  /**
   * 判断一行文本是否为标题。
   * 规则：
   * 1. 长度不超过 80 字符（避免误判长句）
   * 2. 符合正则表达式（以 # 开头、或以 "第X章"、数字编号开头）
   */
  private boolean isHeadingLine(String line) {
    if (line.length() > 80) {
      return false;
    }
    // 表格行（管道分隔）不应被当作标题，否则会生成错误的 section 前缀。
    if (line.contains("|")) {
      return false;
    }
    // 纯数字/年份类行也不应作为标题（如 2025）。
    if (line.matches("^\\d{2,}$")) {
      return false;
    }
    return HEADING_PATTERN.matcher(line).matches();
  }

  /**
   * 去除标题行中的标记符号（如 #），提取纯文本标题。
   */
  private String stripHeadingMarker(String line) {
    return line.replaceFirst("^#{1,6}\\s+", "").trim();
  }

  private int detectHeadingLevel(String line) {
    String trimmed = line == null ? "" : line.trim();
    if (trimmed.startsWith("#")) {
      int lv = 0;
      while (lv < trimmed.length() && trimmed.charAt(lv) == '#') {
        lv++;
      }
      return Math.max(1, Math.min(lv, 6));
    }
    if (trimmed.startsWith("第")) {
      return 1;
    }
    java.util.regex.Matcher m = Pattern.compile("^([0-9]+(?:\\.[0-9]+){0,5})").matcher(trimmed);
    if (m.find()) {
      String number = m.group(1);
      int dots = (int) number.chars().filter(ch -> ch == '.').count();
      return Math.max(1, Math.min(dots + 1, 6));
    }
    return 1;
  }

  private static class SectionBlock {
    private final String title;
    private final String path;
    private final int level;
    private final String content;

    private SectionBlock(String title, String path, int level, String content) {
      this.title = title;
      this.path = path;
      this.level = level;
      this.content = content;
    }
  }

  @lombok.Builder
  @lombok.Getter
  public static class StructuredChunk {
    private String content;
    private String sectionTitle;
    private String sectionPath;
    private String chunkType;
  }
}
