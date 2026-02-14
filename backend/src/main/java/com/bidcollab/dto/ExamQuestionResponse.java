package com.bidcollab.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExamQuestionResponse {
  private Long id;
  private String questionType;
  private String stem;
  private String optionsJson;
  private BigDecimal score;
  private Integer sortIndex;
}
