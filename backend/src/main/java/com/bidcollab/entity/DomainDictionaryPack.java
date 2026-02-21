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
@Table(name = "domain_dictionary_pack")
public class DomainDictionaryPack extends BaseEntity {
  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "scope_type", nullable = false, length = 16)
  private String scopeType;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "created_by")
  private Long createdBy;
}
