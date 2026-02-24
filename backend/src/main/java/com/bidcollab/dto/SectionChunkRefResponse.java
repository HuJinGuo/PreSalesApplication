package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SectionChunkRefResponse {
  private Long id;
  private Long sectionId;
  private Long sectionVersionId;
  private Integer paragraphIndex;
  private Long chunkId;
  private String quoteText;
  private Instant createdAt;
}

