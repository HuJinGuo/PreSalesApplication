package com.bidcollab.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTokenUsageRecordPageResponse {
  private long total;
  private int page;
  private int size;
  private List<AiTokenUsageRecordItem> records;
}
