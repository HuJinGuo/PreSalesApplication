package com.bidcollab.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExportContentParser {
  private static final Pattern IMG_MARKER = Pattern.compile("^\\[img(?:\\s+([^\\]]+))?\\](\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IMG_TAG = Pattern.compile("(?i)<img\\b[^>]*src=[\"']([^\"']+)[\"'][^>]*>");
  private static final Pattern ATTR = Pattern.compile("(\\w+)=(\"[^\"]*\"|'[^']*'|\\S+)");

  private ExportContentParser() {
  }

  public static List<ExportContentBlock> parse(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    String normalized = normalizeHtml(content);
    String[] lines = normalized.split("\\R");
    List<ExportContentBlock> result = new ArrayList<>();
    StringBuilder textBuf = new StringBuilder();

    for (String raw : lines) {
      String line = raw == null ? "" : raw.trim();
      Matcher matcher = IMG_MARKER.matcher(line);
      if (matcher.matches()) {
        flushText(result, textBuf);
        Map<String, String> attrs = parseAttrs(matcher.group(1));
        String url = matcher.group(2);
        Integer width = parseInt(attrs.get("width"));
        String align = normalizeAlign(attrs.get("align"));
        String caption = attrs.getOrDefault("caption", attrs.getOrDefault("alt", ""));
        result.add(ExportContentBlock.builder()
            .image(ExportImageRef.builder()
                .url(url)
                .widthPx(width)
                .align(align)
                .caption(caption)
                .build())
            .build());
      } else {
        if (!line.isEmpty()) {
          if (textBuf.length() > 0) {
            textBuf.append('\n');
          }
          textBuf.append(line);
        }
      }
    }
    flushText(result, textBuf);
    return result;
  }

  private static void flushText(List<ExportContentBlock> result, StringBuilder textBuf) {
    if (textBuf.length() == 0) {
      return;
    }
    result.add(ExportContentBlock.builder().text(textBuf.toString()).build());
    textBuf.setLength(0);
  }

  private static String normalizeHtml(String content) {
    String withMarkers = convertImgTagsToMarkers(content);
    return withMarkers
        .replaceAll("(?i)<br\\s*/?>", "\n")
        .replaceAll("(?i)</p>", "\n")
        .replaceAll("(?i)</div>", "\n")
        .replaceAll("(?i)</li>", "\n")
        .replaceAll("(?i)<li>", "- ")
        .replaceAll("<[^>]+>", "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private static String convertImgTagsToMarkers(String content) {
    Matcher matcher = IMG_TAG.matcher(content);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String tag = matcher.group(0);
      String src = matcher.group(1);
      String width = firstNonBlank(
          attrValue(tag, "data-width"),
          attrValue(tag, "width"),
          extractWidthFromStyle(attrValue(tag, "style")));
      String alt = firstNonBlank(attrValue(tag, "data-caption"), attrValue(tag, "alt"));
      String style = attrValue(tag, "style");
      String align = normalizeAlign(firstNonBlank(attrValue(tag, "data-align"), extractAlignFromStyle(style)));
      StringBuilder marker = new StringBuilder("\n[img");
      if (width != null && !width.isBlank()) {
        marker.append(" width=").append(width.replaceAll("[^0-9]", ""));
      }
      marker.append(" align=").append(align);
      if (alt != null && !alt.isBlank()) {
        marker.append(" caption=\"").append(alt.replace("\"", "'")).append("\"");
      }
      marker.append("]").append(src).append("\n");
      matcher.appendReplacement(sb, Matcher.quoteReplacement(marker.toString()));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String attrValue(String tag, String key) {
    Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(key) + "=[\"']([^\"']+)[\"']");
    Matcher m = p.matcher(tag);
    return m.find() ? m.group(1) : null;
  }

  private static String extractWidthFromStyle(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    Matcher m = Pattern.compile("(?i)\\bwidth\\s*:\\s*(\\d+)\\s*px").matcher(style);
    return m.find() ? m.group(1) : null;
  }

  private static String extractAlignFromStyle(String style) {
    if (style == null || style.isBlank()) {
      return null;
    }
    String low = style.toLowerCase();
    if (low.contains("margin-left: auto") && low.contains("margin-right: auto")) {
      return "center";
    }
    if (low.contains("margin-left: auto")) {
      return "right";
    }
    return "left";
  }

  private static String firstNonBlank(String... values) {
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

  private static Map<String, String> parseAttrs(String attrText) {
    Map<String, String> map = new HashMap<>();
    if (attrText == null || attrText.isBlank()) {
      return map;
    }
    Matcher matcher = ATTR.matcher(attrText);
    while (matcher.find()) {
      String k = matcher.group(1).toLowerCase();
      String v = matcher.group(2);
      if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.substring(1, v.length() - 1);
      }
      map.put(k, v);
    }
    return map;
  }

  private static Integer parseInt(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(v.replaceAll("[^0-9]", ""));
    } catch (Exception ex) {
      return null;
    }
  }

  private static String normalizeAlign(String align) {
    if (align == null || align.isBlank()) {
      return "left";
    }
    String a = align.trim().toLowerCase();
    return "center".equals(a) || "right".equals(a) ? a : "left";
  }
}
