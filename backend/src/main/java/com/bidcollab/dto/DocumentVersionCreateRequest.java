package com.bidcollab.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentVersionCreateRequest {
  @Size(max = 255)
  private String summary;
}
