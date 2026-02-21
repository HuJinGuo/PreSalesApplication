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
@Table(name = "domain_dictionary_entry")
public class DomainDictionaryEntry extends BaseEntity {
  @Column(name = "pack_id", nullable = false)
  private Long packId;

  @Column(nullable = false, length = 64)
  private String category;

  @Column(nullable = false, length = 255)
  private String term;

  @Column(name = "standard_term", length = 255)
  private String standardTerm;

  @Column(nullable = false)
  private Boolean enabled;

  @Column(nullable = false, length = 16)
  private String sourceType;

  @Column(name = "created_by")
  private Long createdBy;
}
