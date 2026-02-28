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
@Table(name = "domain_category")
public class DomainCategory extends BaseEntity {

  @Column(nullable = false, length = 64, unique = true)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false)
  private Integer sortOrder;
}
