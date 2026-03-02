package com.bidcollab.agent.dto;

import lombok.Data;

@Data
public class AgentTaskCreateRequest {
  private Long documentId;
  private Long sectionId;
  private Long knowledgeBaseId;
  private String runMode = "STANDARD";
  private String requirement;
  private String projectParams;
}
