package com.bidcollab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentCreateRequest {
  @NotBlank
  private String name;
  private String docType;
}
