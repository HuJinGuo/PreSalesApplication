package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DictionaryPackResponse {
  private Long id;
  private String code;
  private String name;
  private String scopeType;
  private String status;
  private String description;
  private Instant createdAt;
}
