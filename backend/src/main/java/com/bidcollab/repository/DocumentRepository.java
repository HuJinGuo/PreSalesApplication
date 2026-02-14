package com.bidcollab.repository;

import com.bidcollab.entity.Document;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
  List<Document> findByProjectId(Long projectId);
}
