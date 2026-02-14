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
@Table(name = "project")
public class Project extends BaseEntity {
  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "customer_name", length = 255)
  private String customerName;

  @Column(length = 64)
  private String industry;

  @Column(length = 64)
  private String scale;

  @Column(length = 32)
  private String status;

  @Column(name = "created_by")
  private Long createdBy;
}
