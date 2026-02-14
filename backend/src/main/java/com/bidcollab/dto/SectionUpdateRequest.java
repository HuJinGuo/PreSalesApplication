package com.bidcollab.dto;

import lombok.Data;

@Data
public class SectionUpdateRequest {
  private String title;
  private Integer sortIndex;
  private String status;
}
