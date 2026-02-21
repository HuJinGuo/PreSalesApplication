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
@Table(name = "knowledge_base_dictionary_pack")
public class KnowledgeBaseDictionaryPack extends BaseEntity {
  @Column(name = "knowledge_base_id", nullable = false)
  private Long knowledgeBaseId;

  @Column(name = "pack_id", nullable = false)
  private Long packId;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(nullable = false)
  private Integer priority;

  @Column(name = "created_by")
  private Long createdBy;
}
