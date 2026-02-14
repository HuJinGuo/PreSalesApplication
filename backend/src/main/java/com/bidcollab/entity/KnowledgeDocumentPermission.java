package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_document_permission")
public class KnowledgeDocumentPermission extends BaseEntity {
  @Column(name = "knowledge_document_id", nullable = false)
  private Long knowledgeDocumentId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "created_by")
  private Long createdBy;
}
