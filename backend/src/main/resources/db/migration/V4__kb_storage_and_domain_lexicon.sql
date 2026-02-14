ALTER TABLE knowledge_document
  ADD COLUMN storage_path VARCHAR(1024) NULL;

CREATE TABLE knowledge_base_domain_lexicon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  category VARCHAR(64) NOT NULL,
  term VARCHAR(255) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kb_lexicon_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT uk_kb_lexicon_term UNIQUE (knowledge_base_id, category, term)
);

