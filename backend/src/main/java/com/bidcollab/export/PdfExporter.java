package com.bidcollab.export;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import java.awt.Color;

public class PdfExporter {
  private static final Pattern HEX_COLOR = Pattern.compile("color\\s*:\\s*#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");
  private static final Pattern RGB_COLOR = Pattern.compile(
      "color\\s*:\\s*rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
  private final ExportImageLoader imageLoader;

  public PdfExporter(ExportImageLoader imageLoader) {
    this.imageLoader = imageLoader;
  }

  public Path export(String title, List<FlattenedSection> sections, Path outputPath) throws Exception {
    Files.createDirectories(outputPath.getParent());
    Document pdf = new Document();
    try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
      PdfWriter.getInstance(pdf, out);
      pdf.open();

      BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
      Font titleFont = new Font(baseFont, 16, Font.BOLD);
      Font headingFont = new Font(baseFont, 12, Font.BOLD);
      Font bodyFont = new Font(baseFont, 10, Font.NORMAL);

      pdf.add(new Paragraph(title, titleFont));

      for (FlattenedSection section : sections) {
        pdf.add(new Paragraph(section.getNumber() + " " + section.getTitle(), headingFont));
        writeSectionContent(pdf, section.getContent(), bodyFont);
      }
      pdf.close();
    }
    return outputPath;
  }

  private void writeSectionContent(Document pdf, String content, Font bodyFont) throws Exception {
    if (content == null || content.isBlank()) {
      pdf.add(new Paragraph("", bodyFont));
      return;
    }
    if (!isHtml(content)) {
      List<ExportContentBlock> blocks = ExportContentParser.parse(content);
      if (blocks.isEmpty()) {
        pdf.add(new Paragraph("", bodyFont));
        return;
      }
      for (ExportContentBlock block : blocks) {
        if (block.isImage()) {
          addImage(pdf, block.getImage(), bodyFont);
        } else {
          pdf.add(new Paragraph(block.getText() == null ? "" : block.getText(), bodyFont));
        }
      }
      return;
    }

    Element body = Jsoup.parseBodyFragment(content).body();
    for (Node node : body.childNodes()) {
      appendBlockNode(pdf, node, bodyFont, 0);
    }
  }

  private void appendBlockNode(Document pdf, Node node, Font bodyFont, int listLevel) throws Exception {
    if (node instanceof TextNode textNode) {
      String text = normalizeText(textNode.getWholeText());
      if (!text.isBlank()) {
        pdf.add(new Paragraph(text, bodyFont));
      }
      return;
    }
    if (!(node instanceof Element el)) {
      return;
    }

    String tag = el.normalName();
    if ("img".equals(tag)) {
      addImage(pdf, toImageRef(el), bodyFont);
      return;
    }
    if ("table".equals(tag)) {
      appendTable(pdf, el, bodyFont);
      return;
    }
    if ("ul".equals(tag) || "ol".equals(tag)) {
      boolean ordered = "ol".equals(tag);
      com.lowagie.text.List list = new com.lowagie.text.List(ordered, 18f);
      list.setIndentationLeft(Math.max(0, listLevel) * 12f);
      for (Element li : el.children()) {
        if (!"li".equals(li.normalName())) {
          continue;
        }
        ListItemBuilder listItem = buildPhrase(li.childNodes(), TextStyle.normal(), bodyFont, 10f);
        com.lowagie.text.ListItem item = new com.lowagie.text.ListItem(listItem.phrase);
        list.add(item);
      }
      pdf.add(list);
      return;
    }
    if ("figure".equals(tag)) {
      for (Node child : el.childNodes()) {
        appendBlockNode(pdf, child, bodyFont, listLevel);
      }
      return;
    }

    float size = headingSize(tag);
    TextStyle style = TextStyle.normal();
    if (tag.startsWith("h")) {
      style.bold = true;
    }
    ListItemBuilder phrase = buildPhrase(el.childNodes(), style, bodyFont, size);
    Paragraph p = new Paragraph(phrase.phrase);
    if ("blockquote".equals(tag)) {
      p.setIndentationLeft(18f);
    }
    pdf.add(p);
  }

  private void appendTable(Document pdf, Element tableEl, Font bodyFont) throws Exception {
    List<Element> rows = tableEl.select("> thead > tr, > tbody > tr, > tr");
    if (rows.isEmpty()) {
      rows = tableEl.select("tr");
    }
    if (rows.isEmpty()) {
      return;
    }
    int cols = rows.stream().mapToInt(r -> r.select("> th, > td").size()).max().orElse(1);
    cols = Math.max(1, cols);
    PdfPTable table = new PdfPTable(cols);
    table.setWidthPercentage(100);
    table.setSpacingBefore(8f);
    table.setSpacingAfter(8f);

    for (Element tr : rows) {
      List<Element> cells = tr.select("> th, > td");
      for (int i = 0; i < cols; i++) {
        Element cellEl = i < cells.size() ? cells.get(i) : null;
        Phrase phrase = cellEl == null
            ? new Phrase("", bodyFont)
            : buildPhrase(cellEl.childNodes(), "th".equals(cellEl.normalName()) ? TextStyle.bold() : TextStyle.normal(), bodyFont, 10f).phrase;
        PdfPCell cell = new PdfPCell(phrase);
        if (cellEl != null && "th".equals(cellEl.normalName())) {
          cell.setBackgroundColor(new Color(245, 245, 245));
        }
        table.addCell(cell);
      }
    }
    pdf.add(table);
  }

  private ListItemBuilder buildPhrase(List<Node> nodes, TextStyle baseStyle, Font baseFont, float size) throws Exception {
    Phrase phrase = new Phrase();
    for (Node node : nodes) {
      appendInlineNode(phrase, node, baseStyle, baseFont, size);
    }
    if (phrase.isEmpty()) {
      phrase.add(new com.lowagie.text.Chunk("", fontWithStyle(baseFont, baseStyle, size)));
    }
    return new ListItemBuilder(phrase);
  }

  private void appendInlineNode(Phrase phrase, Node node, TextStyle style, Font baseFont, float size) throws Exception {
    if (node instanceof TextNode textNode) {
      String text = normalizeText(textNode.getWholeText());
      if (!text.isBlank()) {
        phrase.add(new com.lowagie.text.Chunk(text, fontWithStyle(baseFont, style, size)));
      }
      return;
    }
    if (!(node instanceof Element el)) {
      return;
    }
    String tag = el.normalName();
    if ("br".equals(tag)) {
      phrase.add(com.lowagie.text.Chunk.NEWLINE);
      return;
    }
    if ("img".equals(tag)) {
      phrase.add(new com.lowagie.text.Chunk("[图片]"));
      return;
    }

    TextStyle next = style.copy();
    if ("strong".equals(tag) || "b".equals(tag)) {
      next.bold = true;
    }
    if ("em".equals(tag) || "i".equals(tag)) {
      next.italic = true;
    }
    if ("u".equals(tag)) {
      next.underline = true;
    }
    String styleAttr = el.attr("style");
    if (styleAttr != null && !styleAttr.isBlank()) {
      String low = styleAttr.toLowerCase(Locale.ROOT);
      if (low.contains("font-weight: bold") || low.contains("font-weight:700")) {
        next.bold = true;
      }
      if (low.contains("font-style: italic")) {
        next.italic = true;
      }
      if (low.contains("text-decoration: underline")) {
        next.underline = true;
      }
      Color c = parseColor(styleAttr);
      if (c != null) {
        next.color = c;
      }
    }
    for (Node child : el.childNodes()) {
      appendInlineNode(phrase, child, next, baseFont, size);
    }
  }

  private Font fontWithStyle(Font baseFont, TextStyle style, float size) {
    int styleFlags = Font.NORMAL;
    if (style.bold) styleFlags |= Font.BOLD;
    if (style.italic) styleFlags |= Font.ITALIC;
    if (style.underline) styleFlags |= Font.UNDERLINE;
    Color color = style.color != null ? style.color : baseFont.getColor();
    return new Font(baseFont.getBaseFont(), size, styleFlags, color);
  }

  private float headingSize(String tag) {
    return switch (tag) {
      case "h1" -> 18f;
      case "h2" -> 16f;
      case "h3" -> 14f;
      case "h4" -> 13f;
      case "h5" -> 12f;
      case "h6" -> 11f;
      default -> 10f;
    };
  }

  private boolean isHtml(String content) {
    return content != null && content.matches("(?s).*</?[a-zA-Z][^>]*>.*");
  }

  private String normalizeText(String text) {
    if (text == null) return "";
    String normalized = text.replace('\u00A0', ' ');
    if (normalized.trim().isEmpty()) return "";
    return normalized.replaceAll("\\s+", " ");
  }

  private Color parseColor(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    Matcher hex = HEX_COLOR.matcher(style);
    if (hex.find()) {
      String v = hex.group(1);
      if (v.length() == 3) {
        v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
      }
      return new Color(Integer.parseInt(v, 16));
    }
    Matcher rgb = RGB_COLOR.matcher(style);
    if (rgb.find()) {
      int r = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(1))));
      int g = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(2))));
      int b = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(3))));
      return new Color(r, g, b);
    }
    return null;
  }

  private ExportImageRef toImageRef(Element img) {
    String src = firstNonBlank(img.attr("src"), img.attr("data-href"));
    String caption = firstNonBlank(img.attr("data-caption"), img.attr("alt"), img.attr("title"), fileName(src));
    String align = firstNonBlank(img.attr("data-align"), "left");
    Integer width = parseWidth(firstNonBlank(img.attr("data-width"), img.attr("width")));
    return ExportImageRef.builder()
        .url(src)
        .caption(caption)
        .align(align)
        .widthPx(width)
        .build();
  }

  private Integer parseWidth(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
    } catch (Exception ex) {
      return null;
    }
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private String fileName(String src) {
    if (src == null || src.isBlank()) return null;
    String cleaned = src;
    int q = cleaned.indexOf('?');
    if (q >= 0) cleaned = cleaned.substring(0, q);
    int h = cleaned.indexOf('#');
    if (h >= 0) cleaned = cleaned.substring(0, h);
    int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
    return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
  }

  private void addImage(Document pdf, ExportImageRef ref, Font bodyFont) throws Exception {
    byte[] bytes = imageLoader.load(ref.getUrl());
    if (bytes == null || bytes.length == 0) {
      pdf.add(new Paragraph("[图片加载失败] " + ref.getUrl(), bodyFont));
      return;
    }
    Image image = Image.getInstance(bytes);
    float maxWidth = ref.getWidthPx() == null ? 460f : Math.min(Math.max(ref.getWidthPx(), 80), 520);
    image.scaleToFit(maxWidth, 520f);
    if ("center".equalsIgnoreCase(ref.getAlign())) {
      image.setAlignment(Image.ALIGN_CENTER);
    } else if ("right".equalsIgnoreCase(ref.getAlign())) {
      image.setAlignment(Image.ALIGN_RIGHT);
    } else {
      image.setAlignment(Image.ALIGN_LEFT);
    }
    pdf.add(image);
    if (ref.getCaption() != null && !ref.getCaption().isBlank()) {
      Font caption = new Font(bodyFont.getBaseFont(), 9, Font.ITALIC);
      Paragraph cp = new Paragraph(ref.getCaption(), caption);
      cp.setAlignment(image.getAlignment());
      pdf.add(cp);
    }
  }

  private static class TextStyle {
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private Color color;

    private static TextStyle normal() {
      return new TextStyle();
    }

    private static TextStyle bold() {
      TextStyle style = new TextStyle();
      style.bold = true;
      return style;
    }

    private TextStyle copy() {
      TextStyle next = new TextStyle();
      next.bold = this.bold;
      next.italic = this.italic;
      next.underline = this.underline;
      next.color = this.color;
      return next;
    }
  }

  private record ListItemBuilder(Phrase phrase) {}
}
