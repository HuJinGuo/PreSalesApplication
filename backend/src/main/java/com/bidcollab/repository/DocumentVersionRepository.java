package com.bidcollab.repository;

import com.bidcollab.entity.DocumentVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
  List<DocumentVersion> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
