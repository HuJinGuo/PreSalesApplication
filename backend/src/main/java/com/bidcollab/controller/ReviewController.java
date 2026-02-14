package com.bidcollab.controller;

import com.bidcollab.dto.ReviewRequest;
import com.bidcollab.entity.SectionReview;
import com.bidcollab.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {
  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping("/sections/{sectionId}/review/submit")
  public void submit(@PathVariable("sectionId") Long sectionId, @Valid @RequestBody ReviewRequest request) {
    reviewService.submit(sectionId, request);
  }

  @PostMapping("/sections/{sectionId}/review/approve")
  public void approve(@PathVariable("sectionId") Long sectionId, @Valid @RequestBody ReviewRequest request) {
    reviewService.approve(sectionId, request);
  }

  @PostMapping("/sections/{sectionId}/review/reject")
  public void reject(@PathVariable("sectionId") Long sectionId, @Valid @RequestBody ReviewRequest request) {
    reviewService.reject(sectionId, request);
  }

  @GetMapping("/sections/{sectionId}/reviews")
  public List<SectionReview> list(@PathVariable("sectionId") Long sectionId) {
    return reviewService.list(sectionId);
  }
}
