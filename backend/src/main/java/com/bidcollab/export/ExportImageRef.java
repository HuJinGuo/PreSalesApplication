package com.bidcollab.export;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExportImageRef {
  private String url;
  private Integer widthPx;
  private String align;
  private String caption;
}
