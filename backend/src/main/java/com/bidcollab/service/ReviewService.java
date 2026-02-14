package com.bidcollab.service;

import com.bidcollab.dto.ReviewRequest;
import com.bidcollab.entity.Section;
import com.bidcollab.entity.SectionReview;
import com.bidcollab.entity.SectionVersion;
import com.bidcollab.enums.ReviewStatus;
import com.bidcollab.enums.SectionStatus;
import com.bidcollab.repository.SectionRepository;
import com.bidcollab.repository.SectionReviewRepository;
import com.bidcollab.repository.SectionVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
  private final SectionReviewRepository sectionReviewRepository;
  private final SectionRepository sectionRepository;
  private final SectionVersionRepository sectionVersionRepository;
  private final CurrentUserService currentUserService;

  public ReviewService(SectionReviewRepository sectionReviewRepository,
                       SectionRepository sectionRepository,
                       SectionVersionRepository sectionVersionRepository,
                       CurrentUserService currentUserService) {
    this.sectionReviewRepository = sectionReviewRepository;
    this.sectionRepository = sectionRepository;
    this.sectionVersionRepository = sectionVersionRepository;
    this.currentUserService = currentUserService;
  }

  @Transactional
  public void submit(Long sectionId, ReviewRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    SectionVersion version = sectionVersionRepository.findById(request.getVersionId())
        .orElseThrow(EntityNotFoundException::new);
    section.setStatus(SectionStatus.REVIEWING);
    SectionReview review = SectionReview.builder()
        .section(section)
        .version(version)
        .status(ReviewStatus.REVIEWING)
        .comment(request.getComment())
        .reviewedBy(currentUserService.getCurrentUserId())
        .reviewedAt(Instant.now())
        .build();
    sectionReviewRepository.save(review);
  }

  @Transactional
  public void approve(Long sectionId, ReviewRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    SectionVersion version = sectionVersionRepository.findById(request.getVersionId())
        .orElseThrow(EntityNotFoundException::new);
    section.setStatus(SectionStatus.LOCKED);
    SectionReview review = SectionReview.builder()
        .section(section)
        .version(version)
        .status(ReviewStatus.APPROVED)
        .comment(request.getComment())
        .reviewedBy(currentUserService.getCurrentUserId())
        .reviewedAt(Instant.now())
        .build();
    sectionReviewRepository.save(review);
  }

  @Transactional
  public void reject(Long sectionId, ReviewRequest request) {
    Section section = sectionRepository.findById(sectionId).orElseThrow(EntityNotFoundException::new);
    SectionVersion version = sectionVersionRepository.findById(request.getVersionId())
        .orElseThrow(EntityNotFoundException::new);
    section.setStatus(SectionStatus.DRAFT);
    SectionReview review = SectionReview.builder()
        .section(section)
        .version(version)
        .status(ReviewStatus.REJECTED)
        .comment(request.getComment())
        .reviewedBy(currentUserService.getCurrentUserId())
        .reviewedAt(Instant.now())
        .build();
    sectionReviewRepository.save(review);
  }

  public List<SectionReview> list(Long sectionId) {
    return sectionReviewRepository.findBySectionIdOrderByCreatedAtDesc(sectionId);
  }
}
