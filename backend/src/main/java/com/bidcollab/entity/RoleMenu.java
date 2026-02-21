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
@Table(name = "role_menu")
public class RoleMenu extends BaseEntity {
  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @Column(name = "menu_id", nullable = false)
  private Long menuId;
}
