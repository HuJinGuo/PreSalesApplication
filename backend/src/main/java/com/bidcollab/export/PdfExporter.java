package com.bidcollab.export;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PdfExporter {
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
      pdf.add(new Paragraph("目录", headingFont));
      for (FlattenedSection section : sections) {
        pdf.add(new Paragraph(section.getNumber() + " " + section.getTitle(), bodyFont));
      }

      for (FlattenedSection section : sections) {
        pdf.add(new Paragraph(section.getNumber() + " " + section.getTitle(), headingFont));
        List<ExportContentBlock> blocks = ExportContentParser.parse(section.getContent());
        if (blocks.isEmpty()) {
          pdf.add(new Paragraph("", bodyFont));
        }
        for (ExportContentBlock block : blocks) {
          if (block.isImage()) {
            addImage(pdf, block.getImage(), bodyFont);
          } else {
            pdf.add(new Paragraph(block.getText() == null ? "" : block.getText(), bodyFont));
          }
        }
      }
      pdf.close();
    }
    return outputPath;
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
}
