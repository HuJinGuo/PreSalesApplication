package com.bidcollab.export;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.util.Units;

public class DocxExporter {
  private static final Pattern STYLE_WIDTH = Pattern.compile("(?i)\\bwidth\\s*:\\s*(\\d+)\\s*px");
  private final ExportImageLoader imageLoader;

  public DocxExporter(ExportImageLoader imageLoader) {
    this.imageLoader = imageLoader;
  }

  public Path export(String title, List<FlattenedSection> sections, Path outputPath) throws Exception {
    Files.createDirectories(outputPath.getParent());
    try (XWPFDocument doc = new XWPFDocument(); FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
      XWPFParagraph titlePara = doc.createParagraph();
      XWPFRun titleRun = titlePara.createRun();
      titleRun.setText(title);
      titleRun.setBold(true);
      titleRun.setFontSize(16);

      for (FlattenedSection section : sections) {
        XWPFParagraph heading = doc.createParagraph();
        applyHeadingStyle(heading, section.getLevel());
        XWPFRun headingRun = heading.createRun();
        headingRun.setText(section.getNumber() + " " + section.getTitle());
        headingRun.setBold(true);
        headingRun.setFontSize(12 + Math.max(0, 4 - section.getLevel()));
        writeSectionContent(doc, section.getContent());
      }

      doc.write(out);
    }
    return outputPath;
  }

  private void applyHeadingStyle(XWPFParagraph heading, int level) {
    int lv = Math.max(1, Math.min(level, 6));
    heading.setStyle("Heading" + lv);
  }

  private void writeSectionContent(XWPFDocument doc, String content) {
    if (content == null || content.isBlank()) {
      XWPFParagraph body = doc.createParagraph();
      body.createRun().setText("");
      return;
    }
    if (!isHtml(content)) {
      // 兼容旧文本/标记语法：[img ...]url 与普通换行文本
      List<ExportContentBlock> blocks = ExportContentParser.parse(content);
      if (blocks.isEmpty()) {
        XWPFParagraph body = doc.createParagraph();
        body.createRun().setText("");
        return;
      }
      for (ExportContentBlock block : blocks) {
        if (block.isImage()) {
          addImage(doc, block.getImage());
        } else {
          XWPFParagraph body = doc.createParagraph();
          XWPFRun bodyRun = body.createRun();
          writeMultiline(bodyRun, block.getText() == null ? "" : block.getText());
        }
      }
      return;
    }

    Element body = Jsoup.parseBodyFragment(content).body();
    if (body == null || body.childNodeSize() == 0) {
      XWPFParagraph paragraph = doc.createParagraph();
      paragraph.createRun().setText(body == null ? "" : body.text());
      return;
    }
    for (Node node : body.childNodes()) {
      appendBlockNode(doc, node, 0);
    }
  }

  private void appendBlockNode(XWPFDocument doc, Node node, int listLevel) {
    if (node instanceof TextNode textNode) {
      String text = normalizeText(textNode.getWholeText());
      if (!text.isBlank()) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.createRun().setText(text);
      }
      return;
    }
    if (!(node instanceof Element el)) {
      return;
    }

    String tag = el.normalName();
    if ("img".equals(tag)) {
      addImage(doc, toImageRef(el));
      return;
    }
    if ("ul".equals(tag) || "ol".equals(tag)) {
      boolean ordered = "ol".equals(tag);
      int index = 1;
      for (Element li : el.children()) {
        if (!"li".equals(li.normalName())) {
          continue;
        }
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setIndentationLeft(Math.max(0, listLevel) * 360);
        TextStyle base = TextStyle.normal();
        String prefix = ordered ? (index++ + ". ") : "• ";
        XWPFRun pRun = paragraph.createRun();
        pRun.setText(prefix);
        appendInlineNodes(paragraph, li.childNodes(), base, 11);
      }
      return;
    }
    if ("table".equals(tag)) {
      appendTable(doc, el);
      return;
    }
    if ("figure".equals(tag)) {
      for (Node child : el.childNodes()) {
        appendBlockNode(doc, child, listLevel);
      }
      return;
    }

    if ("h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)
        || "p".equals(tag) || "div".equals(tag) || "blockquote".equals(tag) || "li".equals(tag)) {
      XWPFParagraph paragraph = doc.createParagraph();
      paragraph.setAlignment(parseAlign(el));
      if ("blockquote".equals(tag)) {
        paragraph.setIndentationLeft(480);
      }
      int fontSize = headingFontSize(tag);
      TextStyle style = TextStyle.normal();
      if (tag.startsWith("h")) {
        style.bold = true;
      }
      appendInlineNodes(paragraph, el.childNodes(), style, fontSize);
      return;
    }

    for (Node child : el.childNodes()) {
      appendBlockNode(doc, child, listLevel);
    }
  }

  private void appendInlineNodes(XWPFParagraph paragraph, List<Node> nodes, TextStyle style, int fontSize) {
    for (Node node : nodes) {
      appendInlineNode(paragraph, node, style, fontSize);
    }
  }

  private void appendInlineNode(XWPFParagraph paragraph, Node node, TextStyle style, int fontSize) {
    if (node instanceof TextNode textNode) {
      String text = normalizeText(textNode.getWholeText());
      if (text.isBlank()) {
        return;
      }
      XWPFRun run = paragraph.createRun();
      applyRunStyle(run, style, fontSize);
      run.setText(text);
      return;
    }
    if (!(node instanceof Element el)) {
      return;
    }
    String tag = el.normalName();
    if ("br".equals(tag)) {
      paragraph.createRun().addBreak();
      return;
    }
    if ("img".equals(tag)) {
      addInlineImage(paragraph, toImageRef(el));
      return;
    }
    if ("ul".equals(tag) || "ol".equals(tag) || "table".equals(tag) || "figure".equals(tag)) {
      appendBlockNode(paragraph.getDocument(), el, 1);
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
    String styleAttr = el.attr("style").toLowerCase(Locale.ROOT);
    if (styleAttr.contains("font-weight: bold") || styleAttr.contains("font-weight:700")) {
      next.bold = true;
    }
    if (styleAttr.contains("font-style: italic")) {
      next.italic = true;
    }
    if (styleAttr.contains("text-decoration: underline")) {
      next.underline = true;
    }
    String color = parseCssColor(styleAttr);
    if (color != null) {
      next.colorHex = color;
    }
    appendInlineNodes(paragraph, el.childNodes(), next, fontSize);
  }

  private void applyRunStyle(XWPFRun run, TextStyle style, int fontSize) {
    run.setBold(style.bold);
    run.setItalic(style.italic);
    run.setUnderline(style.underline ? org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
        : org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
    if (style.colorHex != null && !style.colorHex.isBlank()) {
      run.setColor(style.colorHex);
    }
    if (fontSize > 0) {
      run.setFontSize(fontSize);
    }
  }

  private void appendTable(XWPFDocument doc, Element tableEl) {
    List<Element> rows = tableEl.select("> thead > tr, > tbody > tr, > tr");
    if (rows.isEmpty()) {
      rows = tableEl.select("tr");
    }
    if (rows.isEmpty()) {
      return;
    }

    int colCount = rows.stream()
        .mapToInt(r -> r.select("> th, > td").size())
        .max()
        .orElse(1);
    colCount = Math.max(1, colCount);

    XWPFTable table = doc.createTable(rows.size(), colCount);
    table.setWidth("100%");

    for (int r = 0; r < rows.size(); r++) {
      Element tr = rows.get(r);
      List<Element> cells = tr.select("> th, > td");
      XWPFTableRow row = table.getRow(r);
      for (int c = 0; c < colCount; c++) {
        XWPFTableCell cell = row.getCell(c);
        while (cell.getParagraphs().size() > 0) {
          cell.removeParagraph(0);
        }
        XWPFParagraph paragraph = cell.addParagraph();
        TextStyle base = TextStyle.normal();
        if (c < cells.size()) {
          Element td = cells.get(c);
          if ("th".equals(td.normalName())) {
            base.bold = true;
          }
          appendInlineNodes(paragraph, td.childNodes(), base, 11);
          if (paragraph.getRuns().isEmpty()) {
            paragraph.createRun().setText(normalizeText(td.text()));
          }
        } else {
          paragraph.createRun().setText("");
        }
      }
    }
  }

  private String parseCssColor(String styleAttr) {
    if (styleAttr == null || styleAttr.isBlank()) {
      return null;
    }
    String style = styleAttr.toLowerCase(Locale.ROOT);
    Matcher hex = Pattern.compile("color\\s*:\\s*#([0-9a-f]{3}|[0-9a-f]{6})").matcher(style);
    if (hex.find()) {
      String value = hex.group(1);
      if (value.length() == 3) {
        return ("" + value.charAt(0) + value.charAt(0)
            + value.charAt(1) + value.charAt(1)
            + value.charAt(2) + value.charAt(2)).toUpperCase(Locale.ROOT);
      }
      return value.toUpperCase(Locale.ROOT);
    }
    Matcher rgb = Pattern.compile("color\\s*:\\s*rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)").matcher(style);
    if (rgb.find()) {
      int r = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(1))));
      int g = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(2))));
      int b = Math.min(255, Math.max(0, Integer.parseInt(rgb.group(3))));
      return String.format("%02X%02X%02X", r, g, b);
    }
    return null;
  }

  private int headingFontSize(String tag) {
    return switch (tag) {
      case "h1" -> 20;
      case "h2" -> 18;
      case "h3" -> 16;
      case "h4" -> 14;
      case "h5" -> 13;
      case "h6" -> 12;
      default -> 11;
    };
  }

  private ParagraphAlignment parseAlign(Element el) {
    String align = el.attr("align");
    if (align == null || align.isBlank()) {
      String style = el.attr("style").toLowerCase(Locale.ROOT);
      if (style.contains("text-align:center")) {
        align = "center";
      } else if (style.contains("text-align:right")) {
        align = "right";
      } else {
        align = "left";
      }
    }
    return toParagraphAlign(align);
  }

  private ExportImageRef toImageRef(Element img) {
    String src = firstNonBlank(img.attr("src"), img.attr("data-href"));
    String caption = firstNonBlank(img.attr("data-caption"), img.attr("alt"), img.attr("title"), fileName(src));
    String align = firstNonBlank(img.attr("data-align"), parentAlign(img), styleAlign(img.attr("style")), "left");
    Integer width = parseWidth(firstNonBlank(img.attr("data-width"), img.attr("width"), styleWidth(img.attr("style"))));
    return ExportImageRef.builder()
        .url(src)
        .caption(caption)
        .align(align)
        .widthPx(width)
        .build();
  }

  private void addInlineImage(XWPFParagraph paragraph, ExportImageRef ref) {
    if (paragraph == null) {
      return;
    }
    byte[] bytes = imageLoader.load(ref.getUrl());
    if (bytes == null || bytes.length == 0) {
      XWPFRun fallback = paragraph.createRun();
      fallback.setText(" [图片加载失败:" + ref.getUrl() + "] ");
      return;
    }
    int[] wh = detectSize(bytes, ref.getWidthPx());
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      XWPFRun run = paragraph.createRun();
      run.addPicture(in, pictureType(ref.getUrl()), "image", Units.toEMU(wh[0]), Units.toEMU(wh[1]));
    } catch (Exception ex) {
      XWPFRun fallback = paragraph.createRun();
      fallback.setText(" [图片插入失败:" + ref.getUrl() + "] ");
    }
  }

  private String parentAlign(Element img) {
    Element parent = img.parent();
    if (parent == null) {
      return null;
    }
    return firstNonBlank(parent.attr("data-align"), styleAlign(parent.attr("style")));
  }

  private String styleAlign(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    String low = style.toLowerCase(Locale.ROOT);
    if (low.contains("margin-left:auto") && low.contains("margin-right:auto")) {
      return "center";
    }
    if (low.contains("margin-left:auto")) {
      return "right";
    }
    return null;
  }

  private String styleWidth(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    Matcher matcher = STYLE_WIDTH.matcher(style);
    return matcher.find() ? matcher.group(1) : null;
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

  private String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    String normalized = text.replace('\u00A0', ' ');
    if (normalized.trim().isEmpty()) {
      return "";
    }
    return normalized.replaceAll("\\s+", " ");
  }

  private boolean isHtml(String content) {
    if (content == null) {
      return false;
    }
    return content.matches("(?s).*</?[a-zA-Z][^>]*>.*");
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private String fileName(String src) {
    if (src == null || src.isBlank()) {
      return null;
    }
    String cleaned = src;
    int q = cleaned.indexOf('?');
    if (q >= 0) cleaned = cleaned.substring(0, q);
    int hash = cleaned.indexOf('#');
    if (hash >= 0) cleaned = cleaned.substring(0, hash);
    int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
    return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
  }

  private void addImage(XWPFDocument doc, ExportImageRef ref) {
    byte[] bytes = imageLoader.load(ref.getUrl());
    if (bytes == null || bytes.length == 0) {
      XWPFParagraph fallback = doc.createParagraph();
      fallback.createRun().setText("[图片加载失败] " + ref.getUrl());
      return;
    }
    XWPFParagraph p = doc.createParagraph();
    p.setAlignment(toParagraphAlign(ref.getAlign()));
    XWPFRun run = p.createRun();
    int[] wh = detectSize(bytes, ref.getWidthPx());
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      run.addPicture(in, pictureType(ref.getUrl()), "image", Units.toEMU(wh[0]), Units.toEMU(wh[1]));
    } catch (Exception ex) {
      XWPFParagraph fallback = doc.createParagraph();
      fallback.createRun().setText("[图片插入失败] " + ref.getUrl());
    }
    if (ref.getCaption() != null && !ref.getCaption().isBlank()) {
      XWPFParagraph cp = doc.createParagraph();
      cp.setAlignment(toParagraphAlign(ref.getAlign()));
      XWPFRun cr = cp.createRun();
      cr.setText(ref.getCaption());
      cr.setItalic(true);
    }
  }

  private int[] detectSize(byte[] bytes, Integer customWidth) {
    int defaultWidth = 520;
    int width = customWidth == null || customWidth < 60 ? defaultWidth : Math.min(customWidth, 900);
    try {
      var image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
        return new int[]{width, 320};
      }
      int h = (int) Math.max(80, (long) width * image.getHeight() / image.getWidth());
      return new int[]{width, h};
    } catch (Exception ex) {
      return new int[]{width, 320};
    }
  }

  private int pictureType(String url) {
    String low = url == null ? "" : url.toLowerCase(Locale.ROOT);
    if (low.endsWith(".png")) return XWPFDocument.PICTURE_TYPE_PNG;
    if (low.endsWith(".gif")) return XWPFDocument.PICTURE_TYPE_GIF;
    if (low.endsWith(".bmp")) return XWPFDocument.PICTURE_TYPE_BMP;
    if (low.endsWith(".webp")) return XWPFDocument.PICTURE_TYPE_PNG;
    return XWPFDocument.PICTURE_TYPE_JPEG;
  }

  private ParagraphAlignment toParagraphAlign(String align) {
    if ("center".equalsIgnoreCase(align)) {
      return ParagraphAlignment.CENTER;
    }
    if ("right".equalsIgnoreCase(align)) {
      return ParagraphAlignment.RIGHT;
    }
    return ParagraphAlignment.LEFT;
  }

  private void writeMultiline(XWPFRun run, String text) {
    if (text == null || text.isBlank()) {
      run.setText("");
      return;
    }
    String[] lines = text.split("\\R", -1);
    run.setText(lines[0]);
    for (int i = 1; i < lines.length; i++) {
      run.addBreak();
      run.setText(lines[i]);
    }
  }

  private static class TextStyle {
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private String colorHex;

    private static TextStyle normal() {
      return new TextStyle();
    }

    private TextStyle copy() {
      TextStyle next = new TextStyle();
      next.bold = this.bold;
      next.italic = this.italic;
      next.underline = this.underline;
      next.colorHex = this.colorHex;
      return next;
    }
  }
}
