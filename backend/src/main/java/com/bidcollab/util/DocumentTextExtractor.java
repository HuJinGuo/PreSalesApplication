package com.bidcollab.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentTextExtractor {
  // [AI-READ] 多格式文档抽取器：为分块与向量化提供统一清洗文本。
  // [AI-READ] 统一入口（上传文件场景）
  /**
   * 统一文档提取入口。
   * <p>
   * 将不同格式（Docx, PDF, PPT, TXT, MD）的文档内容提取并标准化为纯文本，
   * 以便于后续的分块（Chunking）和向量化（Embedding）处理。
   *
   * @param file 上传的 MultipartFile 文件
   * @return 提取并归一化后的纯文本内容
   * @throws IllegalArgumentException 如果文件类型不支持
   * @throws IllegalStateException    如果文件解析失败
   */
  public String extract(MultipartFile file) {
    try {
      return extract(file.getBytes(), file.getOriginalFilename());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse file", ex);
    }
  }

  // [AI-READ] 统一入口（本地文件重建索引场景）
  public String extract(byte[] bytes, String originalFileName) {
    String name = originalFileName == null ? "" : originalFileName.toLowerCase();
    try {
      if (name.endsWith(".docx")) {
        return normalizeForEmbedding(extractDocx(bytes));
      }
      if (name.endsWith(".pdf")) {
        return normalizeForEmbedding(extractPdf(bytes));
      }
      if (name.endsWith(".pptx")) {
        return normalizeForEmbedding(extractPptx(bytes));
      }
      if (name.endsWith(".ppt")) {
        return normalizeForEmbedding(extractPpt(bytes));
      }
      if (name.endsWith(".txt") || name.endsWith(".md")) {
        return normalizeForEmbedding(new String(bytes, StandardCharsets.UTF_8));
      }
      throw new IllegalArgumentException("Unsupported file type: " + name);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse file", ex);
    }
  }

  /**
   * 提取 Word (.docx) 文档内容。
   * <p>
   * 遍历文档中的段落（Paragraphs）和表格（Tables），将它们按阅读顺序拼接。
   * 表格内容会按 "单元格 | 单元格" 的形式拼接，并保留换行结构。
   */
  private String extractDocx(byte[] bytes) throws IOException {
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      StringBuilder sb = new StringBuilder();
      for (IBodyElement element : doc.getBodyElements()) {
        if (element instanceof XWPFParagraph) {
          XWPFParagraph p = (XWPFParagraph) element;
          String text = p.getText();
          if (text != null && !text.isBlank()) {
            sb.append(text.trim()).append('\n');
          }
          continue;
        }
        if (element instanceof XWPFTable) {
          XWPFTable table = (XWPFTable) element;
          table.getRows().forEach(row -> {
            List<String> cells = new ArrayList<>();
            row.getTableCells().forEach(cell -> {
              String txt = cell.getText();
              if (txt != null && !txt.isBlank()) {
                cells.add(txt.trim());
              }
            });
            if (!cells.isEmpty()) {
              sb.append(String.join(" | ", cells)).append('\n');
            }
          });
        }
      }
      return sb.toString();
    }
  }

  /**
   * 提取 PDF 文档内容。
   * <p>
   * 特殊处理逻辑：
   * 1. 逐页提取文本。
   * 2. 统计并移除页眉页脚（通过识别页面中高频出现的重复行）。
   * 3. 尝试合并被意外断行的句子（normalizePdfPage），提升语义连贯性。
   */
  private String extractPdf(byte[] bytes) throws IOException {
    try (PDDocument document = PDDocument.load(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      int pages = document.getNumberOfPages();
      if (pages <= 0) {
        return "";
      }

      List<String> pageTexts = new ArrayList<>();
      // 统计每行文本出现的频率，用于识别页眉/页脚
      Map<String, Integer> repeatedLineFreq = new HashMap<>();
      for (int page = 1; page <= pages; page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        String pageText = stripper.getText(document);
        pageTexts.add(pageText);

        for (String line : pageText.split("\\R")) {
          String normalizedLine = normalizeLine(line);
          // 仅统计短行，避免误伤正文长句
          if (normalizedLine.length() >= 4 && normalizedLine.length() <= 40) {
            repeatedLineFreq.put(normalizedLine, repeatedLineFreq.getOrDefault(normalizedLine, 0) + 1);
          }
        }
      }

      // 如果某行在超过一半的页面中出现（或至少出现3次），则视为页眉/页脚
      int repeatedThreshold = Math.max(3, pages / 2);
      StringBuilder merged = new StringBuilder();
      for (int i = 0; i < pageTexts.size(); i++) {
        // 清洗页面内容：移除页眉页脚，合并断行
        String normalizedPage = normalizePdfPage(pageTexts.get(i), repeatedLineFreq, repeatedThreshold);
        if (!normalizedPage.isBlank()) {
          merged.append(normalizedPage).append("\n\n");
        }
      }
      return merged.toString();
    }
  }

  /**
   * 提取 PowerPoint (.pptx) 幻灯片内容。
   * <p>
   * 按页遍历，提取每页中的所有文本框内容，并在每页前添加 "第X页" 标记，便于定位。
   */
  private String extractPptx(byte[] bytes) throws IOException {
    try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
      StringBuilder sb = new StringBuilder();
      int slideNo = 1;
      for (XSLFSlide slide : show.getSlides()) {
        sb.append("第").append(slideNo++).append("页").append('\n');
        for (XSLFShape shape : slide.getShapes()) {
          if (shape instanceof XSLFTextShape) {
            XSLFTextShape textShape = (XSLFTextShape) shape;
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
              sb.append(text.trim()).append('\n');
            }
          }
        }
        sb.append('\n');
      }
      return sb.toString();
    }
  }

  /**
   * 提取旧版 PowerPoint (.ppt) 幻灯片内容。
   * <p>
   * 逻辑与 extractPptx 相同，但使用 HSLF API 处理旧版 .ppt 格式。
   */
  private String extractPpt(byte[] bytes) throws IOException {
    try (HSLFSlideShow show = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
      StringBuilder sb = new StringBuilder();
      int slideNo = 1;
      for (HSLFSlide slide : show.getSlides()) {
        sb.append("第").append(slideNo++).append("页").append('\n');
        for (HSLFShape shape : slide.getShapes()) {
          if (shape instanceof HSLFTextShape) {
            HSLFTextShape textShape = (HSLFTextShape) shape;
            String text = textShape.getText();
            if (text != null && !text.isBlank()) {
              sb.append(text.trim()).append('\n');
            }
          }
        }
        sb.append('\n');
      }
      return sb.toString();
    }
  }

  /**
   * 归一化单页 PDF 文本。
   * <p>
   * 处理操作：
   * 1. 移除高频重复行（页眉/页脚）。
   * 2. 尝试合并被 PDF 意外断开的句子（shouldMergeLine 判断）。
   * 3. 保留段落间的空行结构。
   *
   * @param pageText          单页原始文本
   * @param repeatedLineFreq  行重复频率统计
   * @param repeatedThreshold 页眉页脚判定阈值
   * @return 归一化后的页面文本
   */
  private String normalizePdfPage(String pageText, Map<String, Integer> repeatedLineFreq, int repeatedThreshold) {
    StringBuilder sb = new StringBuilder();
    String pending = "";
    for (String line : pageText.split("\\R")) {
      String normalized = normalizeLine(line);
      if (normalized.isBlank()) {
        if (!pending.isBlank()) {
          sb.append(pending).append('\n');
          pending = "";
        }
        continue;
      }
      if (repeatedLineFreq.getOrDefault(normalized, 0) >= repeatedThreshold) {
        continue;
      }
      if (pending.isBlank()) {
        pending = normalized;
      } else if (shouldMergeLine(pending, normalized)) {
        pending = pending + " " + normalized;
      } else {
        sb.append(pending).append('\n');
        pending = normalized;
      }
    }
    if (!pending.isBlank()) {
      sb.append(pending).append('\n');
    }
    return sb.toString().trim();
  }

  /**
   * 归一化单行文本：替换不断行空格、去除行末连字符、压缩多余空白。
   */
  private String normalizeLine(String line) {
    if (line == null) {
      return "";
    }
    return line.replace('\u00A0', ' ')
        .replaceAll("-\\s*$", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /**
   * 判断两行是否应该合并。
   * <p>
   * PDF 提取时经常在句中意外断行，合并规则：
   * - 如果前一行以句尾标点结尾（。；: ：），不合并。
   * - 如果当前行像章节标题（"第X章"），不合并。
   * - 如果当前行太短（<8字符），不合并（可能是标题或页码）。
   * - 其他情况合并。
   */
  private boolean shouldMergeLine(String previous, String current) {
    if (previous.endsWith("。") || previous.endsWith("；") || previous.endsWith(":") || previous.endsWith("：")) {
      return false;
    }
    if (current.startsWith("第") && current.contains("章")) {
      return false;
    }
    if (current.length() < 8) {
      return false;
    }
    return true;
  }

  /**
   * 将提取的文本归一化为适合 Embedding 的格式。
   * <p>
   * 操作：
   * 1. 替换不断行空格 (\u00A0)。
   * 2. 压缩连续 3 个以上的换行为双换行。
   * 3. 压缩连续空格和Tab。
   * 4. 截断超过 200万字符的文本，防止向量化超时。
   *
   * @param text 提取出的原始文本
   * @return 归一化后的纯文本
   */
  private String normalizeForEmbedding(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String normalized = text.replace('\u00A0', ' ')
        .replaceAll("\\R{3,}", "\n\n")
        .replaceAll("[ \\t]+", " ")
        .trim();
    if (normalized.length() > 2_000_000) {
      return normalized.substring(0, 2_000_000);
    }
    return normalized;
  }
}
