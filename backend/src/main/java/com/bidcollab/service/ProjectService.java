package com.bidcollab.service;

import com.bidcollab.dto.ProjectCreateRequest;
import com.bidcollab.dto.ProjectResponse;
import com.bidcollab.dto.ProjectUpdateRequest;
import com.bidcollab.entity.Project;
import com.bidcollab.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
  private final ProjectRepository projectRepository;
  private final CurrentUserService currentUserService;

  public ProjectService(ProjectRepository projectRepository, CurrentUserService currentUserService) {
    this.projectRepository = projectRepository;
    this.currentUserService = currentUserService;
  }

  @Transactional
  public ProjectResponse create(ProjectCreateRequest request) {
    if (projectRepository.existsByCode(request.getCode())) {
      throw new IllegalArgumentException("Project code already exists");
    }
    Project project = Project.builder()
        .code(request.getCode())
        .name(request.getName())
        .customerName(request.getCustomerName())
        .industry(request.getIndustry())
        .scale(request.getScale())
        .status("active")
        .createdBy(currentUserService.getCurrentUserId())
        .build();
    projectRepository.save(project);
    return toResponse(project);
  }

  public List<ProjectResponse> list() {
    return projectRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
  }

  public ProjectResponse get(Long id) {
    return toResponse(projectRepository.findById(id).orElseThrow(EntityNotFoundException::new));
  }

  @Transactional
  public ProjectResponse update(Long id, ProjectUpdateRequest request) {
    Project project = projectRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    if (request.getName() != null) project.setName(request.getName());
    if (request.getCustomerName() != null) project.setCustomerName(request.getCustomerName());
    if (request.getIndustry() != null) project.setIndustry(request.getIndustry());
    if (request.getScale() != null) project.setScale(request.getScale());
    if (request.getStatus() != null) project.setStatus(request.getStatus());
    return toResponse(project);
  }

  private ProjectResponse toResponse(Project project) {
    return ProjectResponse.builder()
        .id(project.getId())
        .code(project.getCode())
        .name(project.getName())
        .customerName(project.getCustomerName())
        .industry(project.getIndustry())
        .scale(project.getScale())
        .status(project.getStatus())
        .createdAt(project.getCreatedAt())
        .updatedAt(project.getUpdatedAt())
        .build();
  }
}
