package com.bidcollab.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SectionTemplateResponse {
  private Long id;
  private String name;
  private String description;
  private List<SectionTemplateNode> structure;
  private Instant createdAt;
}

