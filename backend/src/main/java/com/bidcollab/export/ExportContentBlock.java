package com.bidcollab.export;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExportContentBlock {
  private String text;
  private ExportImageRef image;

  public boolean isImage() {
    return image != null;
  }
}
