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
@Table(name = "document_version")
public class DocumentVersion extends BaseEntity {
  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(length = 255)
  private String summary;

  @Column(name = "snapshot_json", columnDefinition = "LONGTEXT", nullable = false)
  private String snapshotJson;

  @Column(name = "created_by")
  private Long createdBy;
}
