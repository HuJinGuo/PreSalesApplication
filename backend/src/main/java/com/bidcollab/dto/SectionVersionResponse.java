package com.bidcollab.dto;

import com.bidcollab.enums.SectionSourceType;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SectionVersionResponse {
  private Long id;
  private Long sectionId;
  private String content;
  private String summary;
  private SectionSourceType sourceType;
  private String sourceRef;
  private Long createdBy;
  private Instant createdAt;
}
