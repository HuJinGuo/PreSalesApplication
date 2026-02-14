package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SectionVersionCreateRequest {
  @NotBlank
  private String content;
  private String summary;
}
