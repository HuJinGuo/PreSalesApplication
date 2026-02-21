package com.bidcollab.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeDocumentResponse {
  private Long id;
  private Long knowledgeBaseId;
  private String title;
  private String sourceType;
  private String fileName;
  private String fileType;
  private String storagePath;
  private String visibility;
  private String indexStatus;
  private String indexMessage;
  private Instant indexedAt;
  private Instant createdAt;
}
