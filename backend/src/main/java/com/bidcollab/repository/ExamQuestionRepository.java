package com.bidcollab.repository;

import com.bidcollab.entity.ExamQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
  List<ExamQuestion> findByPaperIdOrderBySortIndexAsc(Long paperId);
  void deleteByPaperId(Long paperId);

  @Modifying
  @Transactional
  @Query("""
      delete from ExamQuestion q
      where q.paper.id in (
        select p.id from ExamPaper p where p.knowledgeBase.id = :knowledgeBaseId
      )
      """)
  void deleteByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
}
