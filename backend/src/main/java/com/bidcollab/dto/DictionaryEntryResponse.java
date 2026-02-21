package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DictionaryEntryResponse {
  private Long id;
  private Long packId;
  private String category;
  private String term;
  private String standardTerm;
  private Boolean enabled;
  private String sourceType;
  private Instant createdAt;
}
