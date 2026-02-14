package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileUploadResponse {
  private String url;
  private String originalName;
  private long size;
}
