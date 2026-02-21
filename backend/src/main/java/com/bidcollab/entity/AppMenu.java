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
@Table(name = "app_menu")
public class AppMenu extends BaseEntity {
  @Column(name = "parent_id")
  private Long parentId;

  @Column(nullable = false, length = 128)
  private String title;

  @Column(length = 255)
  private String path;

  @Column(length = 64)
  private String icon;

  @Column(name = "sort_index", nullable = false)
  private Integer sortIndex;

  @Column(nullable = false)
  private Boolean visible;
}
