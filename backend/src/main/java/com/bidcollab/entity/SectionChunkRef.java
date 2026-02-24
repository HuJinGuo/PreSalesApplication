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
@Table(name = "section_chunk_ref")
public class SectionChunkRef extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_id", nullable = false)
  private Section section;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "section_version_id")
  private SectionVersion sectionVersion;

  @Column(name = "paragraph_index", nullable = false)
  private Integer paragraphIndex;

  @Column(name = "chunk_id", nullable = false)
  private Long chunkId;

  @Column(name = "quote_text", columnDefinition = "TEXT")
  private String quoteText;

  @Column(name = "created_by")
  private Long createdBy;
}

