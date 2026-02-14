package com.bidcollab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "section_asset")
public class SectionAsset extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id", nullable = false)
  private Section section;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "version_id", nullable = false)
  private SectionVersion version;

  @Column(name = "industry_tag", length = 64)
  private String industryTag;

  @Column(name = "scope_tag", length = 128)
  private String scopeTag;

  @Column(name = "is_winning")
  private Boolean isWinning;

  @Column(length = 255)
  private String keywords;

  @Column(name = "created_by")
  private Long createdBy;
}
