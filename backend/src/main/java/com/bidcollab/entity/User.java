package com.bidcollab.entity;

import com.bidcollab.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "app_user")
public class User extends BaseEntity {
  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "real_name", length = 128)
  private String realName;

  @Column(length = 128)
  private String dept;

  @Column(name = "department_id")
  private Long departmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, columnDefinition = "VARCHAR(32)")
  private UserStatus status;
}
