package com.bidcollab.dto;

import lombok.Data;

@Data
public class DocumentUpdateRequest {
  private String name;
  private String docType;
  private Integer versionNo;
}
