package com.bidcollab.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamPublishResponse {
  private Long paperId;
  private String shareToken;
  private String sharePath;
}
