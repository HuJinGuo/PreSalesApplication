package com.bidcollab.service;

import com.bidcollab.dto.base.CurrentUserMenuResponse;
import com.bidcollab.dto.base.DepartmentDto;
import com.bidcollab.dto.base.DepartmentSaveRequest;
import com.bidcollab.dto.base.MenuDto;
import com.bidcollab.dto.base.MenuSaveRequest;
import com.bidcollab.dto.base.RoleDto;
import com.bidcollab.dto.base.RoleSaveRequest;
import com.bidcollab.dto.base.UserManagementDto;
import com.bidcollab.dto.base.UserSaveRequest;
import com.bidcollab.entity.AppMenu;
import com.bidcollab.entity.Department;
import com.bidcollab.entity.Role;
import com.bidcollab.entity.RoleMenu;
import com.bidcollab.entity.User;
import com.bidcollab.entity.UserMenu;
import com.bidcollab.entity.UserRole;
import com.bidcollab.enums.UserStatus;
import com.bidcollab.repository.AppMenuRepository;
import com.bidcollab.repository.DepartmentRepository;
import com.bidcollab.repository.RoleMenuRepository;
import com.bidcollab.repository.RoleRepository;
import com.bidcollab.repository.UserMenuRepository;
import com.bidcollab.repository.UserRepository;
import com.bidcollab.repository.UserRoleRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BasePermissionService {
  private final DepartmentRepository departmentRepository;
  private final AppMenuRepository appMenuRepository;
  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final RoleMenuRepository roleMenuRepository;
  private final UserRoleRepository userRoleRepository;
  private final UserMenuRepository userMenuRepository;
  private final PasswordEncoder passwordEncoder;

  public BasePermissionService(
      DepartmentRepository departmentRepository,
      AppMenuRepository appMenuRepository,
      RoleRepository roleRepository,
      UserRepository userRepository,
      RoleMenuRepository roleMenuRepository,
      UserRoleRepository userRoleRepository,
      UserMenuRepository userMenuRepository,
      PasswordEncoder passwordEncoder) {
    this.departmentRepository = departmentRepository;
    this.appMenuRepository = appMenuRepository;
    this.roleRepository = roleRepository;
    this.userRepository = userRepository;
    this.roleMenuRepository = roleMenuRepository;
    this.userRoleRepository = userRoleRepository;
    this.userMenuRepository = userMenuRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public List<DepartmentDto> listDepartments() {
    return departmentRepository.findAll().stream()
        .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toDepartmentDto)
        .toList();
  }

  @Transactional
  public DepartmentDto createDepartment(DepartmentSaveRequest request) {
    departmentRepository.findByCode(request.getCode()).ifPresent(it -> {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门编码已存在");
    });
    Department department = Department.builder()
        .code(request.getCode().trim())
        .name(request.getName().trim())
        .managerName(trimToNull(request.getManagerName()))
        .status(request.getStatus().trim())
        .build();
    return toDepartmentDto(departmentRepository.save(department));
  }

  @Transactional
  public DepartmentDto updateDepartment(Long id, DepartmentSaveRequest request) {
    Department department = departmentRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在"));
    departmentRepository.findByCode(request.getCode()).ifPresent(existing -> {
      if (!Objects.equals(existing.getId(), id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门编码已存在");
      }
    });
    department.setCode(request.getCode().trim());
    department.setName(request.getName().trim());
    department.setManagerName(trimToNull(request.getManagerName()));
    department.setStatus(request.getStatus().trim());
    return toDepartmentDto(departmentRepository.save(department));
  }

  @Transactional
  public void deleteDepartment(Long id) {
    Department department = departmentRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在"));
    List<User> users = userRepository.findByDepartmentId(id);
    for (User user : users) {
      user.setDepartmentId(null);
      user.setDept(null);
    }
    userRepository.saveAll(users);
    departmentRepository.delete(department);
  }

  public List<MenuDto> listMenus(boolean onlyVisible) {
    List<AppMenu> allMenus = appMenuRepository.findAllByOrderBySortIndexAscIdAsc();
    if (onlyVisible) {
      allMenus = allMenus.stream().filter(m -> Boolean.TRUE.equals(m.getVisible())).toList();
    }
    return buildMenuTree(allMenus, Collections.emptySet());
  }

  @Transactional
  public MenuDto createMenu(MenuSaveRequest request) {
    if (request.getParentId() != null && !appMenuRepository.existsById(request.getParentId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父菜单不存在");
    }
    AppMenu menu = AppMenu.builder()
        .parentId(request.getParentId())
        .title(request.getTitle().trim())
        .path(trimToNull(request.getPath()))
        .icon(trimToNull(request.getIcon()))
        .sortIndex(request.getSortIndex())
        .visible(request.getVisible())
        .build();
    return toMenuDto(appMenuRepository.save(menu));
  }

  @Transactional
  public MenuDto updateMenu(Long id, MenuSaveRequest request) {
    AppMenu menu = appMenuRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在"));
    if (Objects.equals(id, request.getParentId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "菜单不能设置自己为父节点");
    }
    if (request.getParentId() != null && !appMenuRepository.existsById(request.getParentId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父菜单不存在");
    }
    menu.setParentId(request.getParentId());
    menu.setTitle(request.getTitle().trim());
    menu.setPath(trimToNull(request.getPath()));
    menu.setIcon(trimToNull(request.getIcon()));
    menu.setSortIndex(request.getSortIndex());
    menu.setVisible(request.getVisible());
    return toMenuDto(appMenuRepository.save(menu));
  }

  @Transactional
  public void deleteMenu(Long id) {
    AppMenu menu = appMenuRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在"));
    boolean hasChildren = appMenuRepository.findAllByOrderBySortIndexAscIdAsc().stream()
        .anyMatch(item -> Objects.equals(item.getParentId(), id));
    if (hasChildren) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先删除子菜单");
    }
    roleMenuRepository.deleteByMenuId(id);
    userMenuRepository.deleteByMenuId(id);
    appMenuRepository.delete(menu);
  }

  public List<RoleDto> listRoles() {
    List<Role> roles = roleRepository.findAll().stream()
        .sorted(Comparator.comparing(Role::getId))
        .toList();
    Map<Long, List<Long>> roleMenuMap = roleMenuRepository.findAll().stream()
        .collect(Collectors.groupingBy(RoleMenu::getRoleId,
            Collectors.mapping(RoleMenu::getMenuId, Collectors.toList())));

    return roles.stream().map(role -> RoleDto.builder()
        .id(role.getId())
        .code(role.getCode())
        .name(role.getName())
        .menuIds(roleMenuMap.getOrDefault(role.getId(), List.of()))
        .build()).toList();
  }

  @Transactional
  public RoleDto createRole(RoleSaveRequest request) {
    roleRepository.findByCode(request.getCode().trim()).ifPresent(it -> {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色编码已存在");
    });
    Role role = Role.builder()
        .code(request.getCode().trim())
        .name(request.getName().trim())
        .build();
    Role saved = roleRepository.save(role);
    syncRoleMenus(saved.getId(), request.getMenuIds());
    return RoleDto.builder()
        .id(saved.getId())
        .code(saved.getCode())
        .name(saved.getName())
        .menuIds(distinctIds(request.getMenuIds()))
        .build();
  }

  @Transactional
  public RoleDto updateRole(Long id, RoleSaveRequest request) {
    Role role = roleRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
    roleRepository.findByCode(request.getCode().trim()).ifPresent(existing -> {
      if (!Objects.equals(existing.getId(), id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色编码已存在");
      }
    });
    role.setCode(request.getCode().trim());
    role.setName(request.getName().trim());
    roleRepository.save(role);
    syncRoleMenus(id, request.getMenuIds());
    return RoleDto.builder()
        .id(id)
        .code(role.getCode())
        .name(role.getName())
        .menuIds(distinctIds(request.getMenuIds()))
        .build();
  }

  @Transactional
  public void deleteRole(Long id) {
    Role role = roleRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
    userRoleRepository.deleteByRoleId(id);
    roleMenuRepository.deleteByRoleId(id);
    roleRepository.delete(role);
  }

  public List<UserManagementDto> listUsers() {
    List<User> users = userRepository.findAll().stream()
        .sorted(Comparator.comparing(User::getId))
        .toList();
    Map<Long, Department> departmentMap = departmentRepository.findAll().stream()
        .collect(Collectors.toMap(Department::getId, d -> d));
    Map<Long, Role> roleMap = roleRepository.findAll().stream()
        .collect(Collectors.toMap(Role::getId, r -> r));

    Map<Long, List<Long>> userRoleMap = userRoleRepository.findAll().stream()
        .collect(Collectors.groupingBy(UserRole::getUserId,
            Collectors.mapping(UserRole::getRoleId, Collectors.toList())));

    Map<Long, List<Long>> userMenuMap = userMenuRepository.findAll().stream()
        .collect(Collectors.groupingBy(UserMenu::getUserId,
            Collectors.mapping(UserMenu::getMenuId, Collectors.toList())));

    return users.stream().map(user -> {
      List<Long> roleIds = userRoleMap.getOrDefault(user.getId(), List.of());
      List<String> roleCodes = roleIds.stream()
          .map(roleMap::get)
          .filter(Objects::nonNull)
          .map(Role::getCode)
          .toList();
      Department department = user.getDepartmentId() == null ? null : departmentMap.get(user.getDepartmentId());
      return UserManagementDto.builder()
          .id(user.getId())
          .username(user.getUsername())
          .realName(user.getRealName())
          .status(user.getStatus().name())
          .departmentId(user.getDepartmentId())
          .departmentName(department == null ? null : department.getName())
          .roleIds(roleIds)
          .menuIds(userMenuMap.getOrDefault(user.getId(), List.of()))
          .roleCodes(roleCodes)
          .build();
    }).toList();
  }

  @Transactional
  public UserManagementDto createUser(UserSaveRequest request) {
    String username = request.getUsername().trim();
    if (userRepository.existsByUsername(username)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建用户时必须设置密码");
    }
    Department department = resolveDepartment(request.getDepartmentId());
    User user = User.builder()
        .username(username)
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .realName(trimToNull(request.getRealName()))
        .departmentId(request.getDepartmentId())
        .dept(department == null ? null : department.getName())
        .status(UserStatus.valueOf(request.getStatus().trim().toUpperCase()))
        .build();
    User saved = userRepository.save(user);
    syncUserRoles(saved.getId(), request.getRoleIds());
    syncUserMenus(saved.getId(), request.getMenuIds());
    return getUserDto(saved);
  }

  @Transactional
  public UserManagementDto updateUser(Long id, UserSaveRequest request) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    String username = request.getUsername().trim();
    userRepository.findByUsername(username).ifPresent(existing -> {
      if (!Objects.equals(existing.getId(), id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
      }
    });
    Department department = resolveDepartment(request.getDepartmentId());

    user.setUsername(username);
    user.setRealName(trimToNull(request.getRealName()));
    user.setDepartmentId(request.getDepartmentId());
    user.setDept(department == null ? null : department.getName());
    user.setStatus(UserStatus.valueOf(request.getStatus().trim().toUpperCase()));
    if (request.getPassword() != null && !request.getPassword().isBlank()) {
      user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    }
    userRepository.save(user);
    syncUserRoles(id, request.getRoleIds());
    syncUserMenus(id, request.getMenuIds());
    return getUserDto(user);
  }

  @Transactional
  public void deleteUser(Long id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    userRoleRepository.deleteByUserId(id);
    userMenuRepository.deleteByUserId(id);
    userRepository.delete(user);
  }

  public CurrentUserMenuResponse getCurrentUserMenus(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

    List<AppMenu> allVisibleMenus = appMenuRepository.findAllByOrderBySortIndexAscIdAsc().stream()
        .filter(menu -> Boolean.TRUE.equals(menu.getVisible()))
        .toList();

    List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
    Set<Long> menuIds = new HashSet<>();
    if (!userRoles.isEmpty()) {
      List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
      menuIds.addAll(roleMenuRepository.findByRoleIdIn(roleIds).stream().map(RoleMenu::getMenuId).toList());
    }
    menuIds.addAll(userMenuRepository.findByUserId(user.getId()).stream().map(UserMenu::getMenuId).toList());

    // 没配置权限时，给管理员和历史账号保底菜单，避免空白侧栏。
    if (menuIds.isEmpty() || "admin".equalsIgnoreCase(user.getUsername())) {
      menuIds.clear();
      menuIds.addAll(allVisibleMenus.stream().map(AppMenu::getId).toList());
    }

    List<MenuDto> menuTree = buildMenuTree(allVisibleMenus, menuIds);
    return CurrentUserMenuResponse.builder()
        .username(user.getUsername())
        .realName(user.getRealName())
        .menus(menuTree)
        .build();
  }

  private UserManagementDto getUserDto(User user) {
    Department department = user.getDepartmentId() == null
        ? null
        : departmentRepository.findById(user.getDepartmentId()).orElse(null);
    List<Long> roleIds = userRoleRepository.findByUserId(user.getId()).stream().map(UserRole::getRoleId).toList();
    Map<Long, Role> roleMap = roleRepository.findAllById(roleIds).stream()
        .collect(Collectors.toMap(Role::getId, r -> r));
    List<Long> menuIds = userMenuRepository.findByUserId(user.getId()).stream().map(UserMenu::getMenuId).toList();

    return UserManagementDto.builder()
        .id(user.getId())
        .username(user.getUsername())
        .realName(user.getRealName())
        .status(user.getStatus().name())
        .departmentId(user.getDepartmentId())
        .departmentName(department == null ? null : department.getName())
        .roleIds(roleIds)
        .menuIds(menuIds)
        .roleCodes(roleIds.stream().map(roleMap::get).filter(Objects::nonNull).map(Role::getCode).toList())
        .build();
  }

  private Department resolveDepartment(Long departmentId) {
    if (departmentId == null) {
      return null;
    }
    return departmentRepository.findById(departmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门不存在"));
  }

  private void syncRoleMenus(Long roleId, Collection<Long> menuIds) {
    List<Long> distinctMenuIds = distinctIds(menuIds);
    if (!distinctMenuIds.isEmpty()) {
      long count = appMenuRepository.findAllById(distinctMenuIds).size();
      if (count != distinctMenuIds.size()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效菜单ID");
      }
    }
    roleMenuRepository.deleteByRoleId(roleId);
    List<RoleMenu> mappings = distinctMenuIds.stream()
        .map(menuId -> RoleMenu.builder().roleId(roleId).menuId(menuId).build())
        .toList();
    roleMenuRepository.saveAll(mappings);
  }

  private void syncUserRoles(Long userId, Collection<Long> roleIds) {
    List<Long> distinctRoleIds = distinctIds(roleIds);
    if (!distinctRoleIds.isEmpty()) {
      long count = roleRepository.findAllById(distinctRoleIds).size();
      if (count != distinctRoleIds.size()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效角色ID");
      }
    }
    userRoleRepository.deleteByUserId(userId);
    List<UserRole> mappings = distinctRoleIds.stream()
        .map(roleId -> UserRole.builder().userId(userId).roleId(roleId).build())
        .toList();
    userRoleRepository.saveAll(mappings);
  }

  private void syncUserMenus(Long userId, Collection<Long> menuIds) {
    List<Long> distinctMenuIds = distinctIds(menuIds);
    if (!distinctMenuIds.isEmpty()) {
      long count = appMenuRepository.findAllById(distinctMenuIds).size();
      if (count != distinctMenuIds.size()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效菜单ID");
      }
    }
    userMenuRepository.deleteByUserId(userId);
    List<UserMenu> mappings = distinctMenuIds.stream()
        .map(menuId -> UserMenu.builder().userId(userId).menuId(menuId).build())
        .toList();
    userMenuRepository.saveAll(mappings);
  }

  private List<Long> distinctIds(Collection<Long> ids) {
    if (ids == null) {
      return List.of();
    }
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  private List<MenuDto> buildMenuTree(List<AppMenu> menus, Set<Long> allowedMenuIds) {
    Map<Long, AppMenu> menuMap = menus.stream().collect(Collectors.toMap(AppMenu::getId, it -> it));
    Set<Long> expandedAllowed = expandWithAncestors(allowedMenuIds, menuMap);

    Map<Long, MenuDto> dtoMap = new LinkedHashMap<>();
    for (AppMenu menu : menus) {
      if (!expandedAllowed.isEmpty() && !expandedAllowed.contains(menu.getId())) {
        continue;
      }
      dtoMap.put(menu.getId(), toMenuDto(menu));
    }

    List<MenuDto> roots = new ArrayList<>();
    for (MenuDto dto : dtoMap.values()) {
      if (dto.getParentId() == null || !dtoMap.containsKey(dto.getParentId())) {
        roots.add(dto);
      } else {
        dtoMap.get(dto.getParentId()).getChildren().add(dto);
      }
    }

    sortMenuTree(roots);
    return roots;
  }

  private Set<Long> expandWithAncestors(Set<Long> allowedMenuIds, Map<Long, AppMenu> menuMap) {
    if (allowedMenuIds == null || allowedMenuIds.isEmpty()) {
      return Collections.emptySet();
    }
    Set<Long> result = new HashSet<>(allowedMenuIds);
    for (Long menuId : new HashSet<>(allowedMenuIds)) {
      Long parentId = Optional.ofNullable(menuMap.get(menuId)).map(AppMenu::getParentId).orElse(null);
      while (parentId != null) {
        result.add(parentId);
        parentId = Optional.ofNullable(menuMap.get(parentId)).map(AppMenu::getParentId).orElse(null);
      }
    }
    return result;
  }

  private void sortMenuTree(List<MenuDto> nodes) {
    nodes.sort(Comparator.comparing(MenuDto::getSortIndex).thenComparing(MenuDto::getId));
    for (MenuDto node : nodes) {
      sortMenuTree(node.getChildren());
    }
  }

  private DepartmentDto toDepartmentDto(Department department) {
    return DepartmentDto.builder()
        .id(department.getId())
        .code(department.getCode())
        .name(department.getName())
        .managerName(department.getManagerName())
        .status(department.getStatus())
        .build();
  }

  private MenuDto toMenuDto(AppMenu menu) {
    return MenuDto.builder()
        .id(menu.getId())
        .parentId(menu.getParentId())
        .title(menu.getTitle())
        .path(menu.getPath())
        .icon(menu.getIcon())
        .sortIndex(menu.getSortIndex())
        .visible(menu.getVisible())
        .build();
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
