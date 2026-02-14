package com.bidcollab.service;

import com.bidcollab.entity.User;
import com.bidcollab.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
  private final UserRepository userRepository;

  public CurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public Optional<User> getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    String username = auth.getName();
    return userRepository.findByUsername(username);
  }

  public Long getCurrentUserId() {
    return getCurrentUser().map(User::getId).orElse(null);
  }
}
