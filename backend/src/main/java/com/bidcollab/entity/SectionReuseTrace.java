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
@Table(name = "section_reuse_trace")
public class SectionReuseTrace extends BaseEntity {
  @Column(name = "target_section_id", nullable = false)
  private Long targetSectionId;

  @Column(name = "target_version_id", nullable = false)
  private Long targetVersionId;

  @Column(name = "source_project_id")
  private Long sourceProjectId;

  @Column(name = "source_document_id")
  private Long sourceDocumentId;

  @Column(name = "source_section_id")
  private Long sourceSectionId;

  @Column(name = "source_version_id")
  private Long sourceVersionId;

  @Column(name = "created_by")
  private Long createdBy;
}
