package com.bidcollab.export;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.util.Units;

public class DocxExporter {
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

      XWPFParagraph tocTitle = doc.createParagraph();
      XWPFRun tocRun = tocTitle.createRun();
      tocRun.setText("目录");
      tocRun.setBold(true);
      tocRun.setFontSize(14);

      for (FlattenedSection section : sections) {
        XWPFParagraph tocItem = doc.createParagraph();
        XWPFRun tocItemRun = tocItem.createRun();
        tocItemRun.setText(section.getNumber() + " " + section.getTitle());
      }

      for (FlattenedSection section : sections) {
        XWPFParagraph heading = doc.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setText(section.getNumber() + " " + section.getTitle());
        headingRun.setBold(true);
        headingRun.setFontSize(12 + Math.max(0, 4 - section.getLevel()));

        List<ExportContentBlock> blocks = ExportContentParser.parse(section.getContent());
        if (blocks.isEmpty()) {
          XWPFParagraph body = doc.createParagraph();
          body.createRun().setText("");
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
      }

      doc.write(out);
    }
    return outputPath;
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
}
