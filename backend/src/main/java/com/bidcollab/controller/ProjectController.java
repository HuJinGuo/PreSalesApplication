package com.bidcollab.controller;

import com.bidcollab.dto.ProjectCreateRequest;
import com.bidcollab.dto.ProjectResponse;
import com.bidcollab.dto.ProjectUpdateRequest;
import com.bidcollab.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @PostMapping
  public ProjectResponse create(@Valid @RequestBody ProjectCreateRequest request) {
    return projectService.create(request);
  }

  @GetMapping
  public List<ProjectResponse> list() {
    return projectService.list();
  }

  @GetMapping("/{id}")
  public ProjectResponse get(@PathVariable("id") Long id) {
    return projectService.get(id);
  }

  @PutMapping("/{id}")
  public ProjectResponse update(@PathVariable("id") Long id, @RequestBody ProjectUpdateRequest request) {
    return projectService.update(id, request);
  }
}
