ALTER TABLE exam_paper
  ADD COLUMN share_token VARCHAR(64) NULL,
  ADD COLUMN is_published TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN published_at TIMESTAMP NULL;

CREATE UNIQUE INDEX uk_exam_paper_share_token ON exam_paper(share_token);

ALTER TABLE exam_submission
  ADD COLUMN submitter_name VARCHAR(128) NULL;

ALTER TABLE knowledge_document
  ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';

CREATE TABLE knowledge_document_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_document_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kdp_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id),
  CONSTRAINT uk_kdp_doc_user UNIQUE (knowledge_document_id, user_id)
);
