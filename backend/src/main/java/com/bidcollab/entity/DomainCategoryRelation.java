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
@Table(name = "domain_category_relation")
public class DomainCategoryRelation extends BaseEntity {
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "source_category_id", nullable = false)
  private DomainCategory sourceCategoryRef;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "target_category_id", nullable = false)
  private DomainCategory targetCategoryRef;

  @Column(name = "source_category", nullable = false, length = 64)
  private String sourceCategory;

  @Column(name = "target_category", nullable = false, length = 64)
  private String targetCategory;

  @Column(name = "relation_label", nullable = false, length = 64)
  private String relationLabel;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(name = "created_by")
  private Long createdBy;
}
