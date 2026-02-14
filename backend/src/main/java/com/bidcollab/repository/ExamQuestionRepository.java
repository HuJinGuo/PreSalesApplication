package com.bidcollab.repository;

import com.bidcollab.entity.ExamQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
  List<ExamQuestion> findByPaperIdOrderBySortIndexAsc(Long paperId);
  void deleteByPaperId(Long paperId);
}
