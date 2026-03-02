package com.bidcollab.repository;

import com.bidcollab.entity.AgentWorkflowTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface AgentWorkflowTaskRepository extends JpaRepository<AgentWorkflowTask, Long> {
  List<AgentWorkflowTask> findByDocumentIdOrderByCreatedAtDesc(Long documentId, Pageable pageable);
}
