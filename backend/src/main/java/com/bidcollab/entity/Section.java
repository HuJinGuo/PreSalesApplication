package com.bidcollab.entity;

import com.bidcollab.enums.SectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "section")
public class Section extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", nullable = false)
  private Document document;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Section parent;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false)
  private Integer level;

  @Column(name = "sort_index", nullable = false)
  private Integer sortIndex;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "current_version_id")
  private SectionVersion currentVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
  private SectionStatus status;

  @Column(name = "locked_by")
  private Long lockedBy;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "created_by")
  private Long createdBy;
}
