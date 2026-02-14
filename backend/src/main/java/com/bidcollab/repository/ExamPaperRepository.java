package com.bidcollab.repository;

import com.bidcollab.entity.ExamPaper;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamPaperRepository extends JpaRepository<ExamPaper, Long> {
  List<ExamPaper> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId);
  java.util.Optional<ExamPaper> findByShareToken(String shareToken);
}
