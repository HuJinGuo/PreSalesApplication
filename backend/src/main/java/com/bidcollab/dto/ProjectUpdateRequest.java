package com.bidcollab.dto;

import lombok.Data;

@Data
public class ProjectUpdateRequest {
  private String name;
  private String customerName;
  private String industry;
  private String scale;
  private String status;
}
