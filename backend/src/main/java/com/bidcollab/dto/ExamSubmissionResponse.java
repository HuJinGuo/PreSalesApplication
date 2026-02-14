package com.bidcollab.dto;

import com.bidcollab.enums.ExamSubmissionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamSubmissionResponse {
  private Long id;
  private Long paperId;
  private String submitterName;
  private Integer rank;
  private BigDecimal score;
  private String aiFeedback;
  private ExamSubmissionStatus status;
  private Instant submittedAt;
  private Instant gradedAt;
}
