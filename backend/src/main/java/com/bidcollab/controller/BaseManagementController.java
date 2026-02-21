package com.bidcollab.controller;

import com.bidcollab.dto.base.CurrentUserMenuResponse;
import com.bidcollab.dto.base.DepartmentDto;
import com.bidcollab.dto.base.DepartmentSaveRequest;
import com.bidcollab.dto.base.MenuDto;
import com.bidcollab.dto.base.MenuSaveRequest;
import com.bidcollab.dto.base.RoleDto;
import com.bidcollab.dto.base.RoleSaveRequest;
import com.bidcollab.dto.base.UserManagementDto;
import com.bidcollab.dto.base.UserSaveRequest;
import com.bidcollab.service.BasePermissionService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/base")
public class BaseManagementController {
  private final BasePermissionService basePermissionService;

  public BaseManagementController(BasePermissionService basePermissionService) {
    this.basePermissionService = basePermissionService;
  }

  @GetMapping("/departments")
  public List<DepartmentDto> listDepartments() {
    return basePermissionService.listDepartments();
  }

  @PostMapping("/departments")
  public DepartmentDto createDepartment(@Valid @RequestBody DepartmentSaveRequest request) {
    return basePermissionService.createDepartment(request);
  }

  @PutMapping("/departments/{id}")
  public DepartmentDto updateDepartment(@PathVariable("id") Long id, @Valid @RequestBody DepartmentSaveRequest request) {
    return basePermissionService.updateDepartment(id, request);
  }

  @DeleteMapping("/departments/{id}")
  public void deleteDepartment(@PathVariable("id") Long id) {
    basePermissionService.deleteDepartment(id);
  }

  @GetMapping("/menus")
  public List<MenuDto> listMenus(@RequestParam(name = "onlyVisible", required = false, defaultValue = "false") boolean onlyVisible) {
    return basePermissionService.listMenus(onlyVisible);
  }

  @PostMapping("/menus")
  public MenuDto createMenu(@Valid @RequestBody MenuSaveRequest request) {
    return basePermissionService.createMenu(request);
  }

  @PutMapping("/menus/{id}")
  public MenuDto updateMenu(@PathVariable("id") Long id, @Valid @RequestBody MenuSaveRequest request) {
    return basePermissionService.updateMenu(id, request);
  }

  @DeleteMapping("/menus/{id}")
  public void deleteMenu(@PathVariable("id") Long id) {
    basePermissionService.deleteMenu(id);
  }

  @GetMapping("/roles")
  public List<RoleDto> listRoles() {
    return basePermissionService.listRoles();
  }

  @PostMapping("/roles")
  public RoleDto createRole(@Valid @RequestBody RoleSaveRequest request) {
    return basePermissionService.createRole(request);
  }

  @PutMapping("/roles/{id}")
  public RoleDto updateRole(@PathVariable("id") Long id, @Valid @RequestBody RoleSaveRequest request) {
    return basePermissionService.updateRole(id, request);
  }

  @DeleteMapping("/roles/{id}")
  public void deleteRole(@PathVariable("id") Long id) {
    basePermissionService.deleteRole(id);
  }

  @GetMapping("/users")
  public List<UserManagementDto> listUsers() {
    return basePermissionService.listUsers();
  }

  @PostMapping("/users")
  public UserManagementDto createUser(@Valid @RequestBody UserSaveRequest request) {
    return basePermissionService.createUser(request);
  }

  @PutMapping("/users/{id}")
  public UserManagementDto updateUser(@PathVariable("id") Long id, @Valid @RequestBody UserSaveRequest request) {
    return basePermissionService.updateUser(id, request);
  }

  @DeleteMapping("/users/{id}")
  public void deleteUser(@PathVariable("id") Long id) {
    basePermissionService.deleteUser(id);
  }

  @GetMapping("/current-user-menus")
  public CurrentUserMenuResponse currentUserMenus(Principal principal) {
    return basePermissionService.getCurrentUserMenus(principal.getName());
  }
}
