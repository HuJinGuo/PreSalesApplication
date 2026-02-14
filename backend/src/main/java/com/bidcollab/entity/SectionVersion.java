package com.bidcollab.entity;

import com.bidcollab.enums.SectionSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "section_version")
public class SectionVersion extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id", nullable = false)
  private Section section;

  @Column(columnDefinition = "LONGTEXT", nullable = false)
  private String content;

  @Column(length = 255)
  private String summary;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", length = 32, nullable = false, columnDefinition = "VARCHAR(32)")
  private SectionSourceType sourceType;

  @Column(name = "source_ref", length = 255)
  private String sourceRef;

  @Column(name = "created_by")
  private Long createdBy;
}
