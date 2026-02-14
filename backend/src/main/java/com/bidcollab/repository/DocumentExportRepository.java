package com.bidcollab.repository;

import com.bidcollab.entity.DocumentExport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentExportRepository extends JpaRepository<DocumentExport, Long> {
  List<DocumentExport> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
