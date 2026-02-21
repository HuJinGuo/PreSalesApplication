package com.bidcollab.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DictionaryBatchUpsertResponse {
  private int total;
  private int success;
  private int failed;

  @Builder.Default
  private List<String> errors = new ArrayList<>();
}
