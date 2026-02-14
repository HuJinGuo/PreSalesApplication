package com.bidcollab.repository;

import com.bidcollab.entity.ExamSubmission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
  List<ExamSubmission> findByPaperIdOrderBySubmittedAtDesc(Long paperId);
  void deleteByPaperId(Long paperId);
}
