package com.bidcollab.entity;

import com.bidcollab.enums.ReviewStatus;
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
@Table(name = "section_review")
public class SectionReview extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id", nullable = false)
  private Section section;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "version_id", nullable = false)
  private SectionVersion version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
  private ReviewStatus status;

  @Column(columnDefinition = "TEXT")
  private String comment;

  @Column(name = "reviewed_by")
  private Long reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;
}
