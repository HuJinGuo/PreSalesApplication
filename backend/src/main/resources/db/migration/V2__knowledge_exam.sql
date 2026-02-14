CREATE TABLE knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  file_type VARCHAR(32),
  content LONGTEXT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_document_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

CREATE TABLE knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  knowledge_document_id BIGINT,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  embedding_json LONGTEXT NOT NULL,
  embedding_dim INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_chunk_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_knowledge_chunk_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id)
);

CREATE TABLE exam_paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  instructions TEXT,
  total_score DECIMAL(10,2) NOT NULL,
  generated_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_exam_paper_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

CREATE TABLE exam_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  question_type VARCHAR(32) NOT NULL,
  stem TEXT NOT NULL,
  options_json TEXT,
  reference_answer TEXT,
  score DECIMAL(10,2) NOT NULL,
  sort_index INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_exam_question_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id)
);

CREATE TABLE exam_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  submitter_id BIGINT,
  answers_json LONGTEXT NOT NULL,
  score DECIMAL(10,2),
  ai_feedback LONGTEXT,
  status VARCHAR(32) NOT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  graded_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_exam_submission_paper FOREIGN KEY (paper_id) REFERENCES exam_paper(id)
);
