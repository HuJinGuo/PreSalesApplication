package com.bidcollab.controller;

import com.bidcollab.dto.LoginRequest;
import com.bidcollab.dto.LoginResponse;
import com.bidcollab.entity.Role;
import com.bidcollab.entity.User;
import com.bidcollab.entity.UserRole;
import com.bidcollab.enums.UserStatus;
import com.bidcollab.repository.RoleRepository;
import com.bidcollab.repository.UserRepository;
import com.bidcollab.repository.UserRoleRepository;
import com.bidcollab.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthController(
      AuthenticationManager authenticationManager,
      JwtUtil jwtUtil,
      UserRepository userRepository,
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository,
      PasswordEncoder passwordEncoder) {
    this.authenticationManager = authenticationManager;
    this.jwtUtil = jwtUtil;
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
    String token = jwtUtil.generateToken(auth.getName());
    return LoginResponse.builder().token(token).username(auth.getName()).build();
  }

  @PostMapping("/register")
  public LoginResponse register(@Valid @RequestBody LoginRequest request) {
    String username = request.getUsername().trim();
    if (userRepository.existsByUsername(username)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
    }

    User user = User.builder()
        .username(username)
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .realName(username)
        .status(UserStatus.ACTIVE)
        .build();
    User savedUser = userRepository.save(user);

    Role preSalesRole = roleRepository.findByCode("PRE_SALES")
        .orElseGet(() -> roleRepository.save(Role.builder().code("PRE_SALES").name("售前工程师").build()));
    userRoleRepository.save(UserRole.builder().userId(savedUser.getId()).roleId(preSalesRole.getId()).build());

    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
    String token = jwtUtil.generateToken(auth.getName());
    return LoginResponse.builder().token(token).username(auth.getName()).build();
  }
}
