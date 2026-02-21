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
@Table(name = "user_menu")
public class UserMenu extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "menu_id", nullable = false)
  private Long menuId;
}
