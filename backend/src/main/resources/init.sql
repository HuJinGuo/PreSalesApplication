CREATE TABLE IF NOT EXISTS project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  customer_name VARCHAR(255),
  industry VARCHAR(64),
  scale VARCHAR(64),
  status VARCHAR(32),
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(128),
  dept VARCHAR(128),
  department_id BIGINT,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  doc_type VARCHAR(64),
  version_no INT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_document_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE IF NOT EXISTS section (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  parent_id BIGINT,
  title VARCHAR(255) NOT NULL,
  level INT NOT NULL,
  sort_index INT NOT NULL,
  current_version_id BIGINT,
  status VARCHAR(32) NOT NULL,
  locked_by BIGINT,
  locked_at TIMESTAMP NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_section_document FOREIGN KEY (document_id) REFERENCES document(id),
  CONSTRAINT fk_section_parent FOREIGN KEY (parent_id) REFERENCES section(id)
);

CREATE TABLE IF NOT EXISTS section_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  section_id BIGINT NOT NULL,
  content LONGTEXT NOT NULL,
  summary VARCHAR(255),
  source_type VARCHAR(32) NOT NULL,
  source_ref VARCHAR(255),
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_section_version_section FOREIGN KEY (section_id) REFERENCES section(id)
);

CREATE TABLE IF NOT EXISTS section_chunk_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  section_id BIGINT NOT NULL,
  section_version_id BIGINT NULL,
  paragraph_index INT NOT NULL,
  chunk_id BIGINT NOT NULL,
  quote_text TEXT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_section_chunk_ref_section FOREIGN KEY (section_id) REFERENCES section(id),
  CONSTRAINT fk_section_chunk_ref_version FOREIGN KEY (section_version_id) REFERENCES section_version(id)
);
SET @idx_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'section_chunk_ref'
    AND INDEX_NAME = 'idx_section_chunk_ref_section'
);
SET @sql = IF(@idx_exists = 0,
  'CREATE INDEX idx_section_chunk_ref_section ON section_chunk_ref(section_id, paragraph_index)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'section_chunk_ref'
    AND INDEX_NAME = 'idx_section_chunk_ref_chunk'
);
SET @sql = IF(@idx_exists = 0,
  'CREATE INDEX idx_section_chunk_ref_chunk ON section_chunk_ref(chunk_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS section_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  section_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  industry_tag VARCHAR(64),
  scope_tag VARCHAR(128),
  is_winning TINYINT(1),
  keywords VARCHAR(255),
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_section_asset_section FOREIGN KEY (section_id) REFERENCES section(id),
  CONSTRAINT fk_section_asset_version FOREIGN KEY (version_id) REFERENCES section_version(id)
);

CREATE TABLE IF NOT EXISTS section_reuse_trace (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_section_id BIGINT NOT NULL,
  target_version_id BIGINT NOT NULL,
  source_project_id BIGINT,
  source_document_id BIGINT,
  source_section_id BIGINT,
  source_version_id BIGINT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS section_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  section_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  comment TEXT,
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_section_review_section FOREIGN KEY (section_id) REFERENCES section(id),
  CONSTRAINT fk_section_review_version FOREIGN KEY (version_id) REFERENCES section_version(id)
);

CREATE TABLE IF NOT EXISTS role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS project_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_in_project VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE IF NOT EXISTS document_export (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  format VARCHAR(16) NOT NULL,
  version_no VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  file_path VARCHAR(512),
  error_message TEXT,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_document_export_document FOREIGN KEY (document_id) REFERENCES document(id)
);

CREATE TABLE IF NOT EXISTS ai_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  section_id BIGINT,
  source_version_id BIGINT,
  result_version_id BIGINT,
  prompt LONGTEXT,
  response LONGTEXT,
  status VARCHAR(16) NOT NULL,
  error_message TEXT,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_workflow_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  section_id BIGINT NULL,
  knowledge_base_id BIGINT NULL,
  run_mode VARCHAR(32) NOT NULL,
  requirement TEXT NULL,
  project_params LONGTEXT NULL,
  max_iterations INT NOT NULL DEFAULT 10,
  current_iteration INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  final_summary TEXT NULL,
  error_message TEXT NULL,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_agent_workflow_task_doc (document_id),
  INDEX idx_agent_workflow_task_status (status),
  INDEX idx_agent_workflow_task_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_workflow_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  iteration_no INT NOT NULL DEFAULT 0,
  step_code VARCHAR(64) NOT NULL,
  step_name VARCHAR(128) NOT NULL,
  step_type VARCHAR(32) NOT NULL,
  tool_name VARCHAR(64) NULL,
  status VARCHAR(16) NOT NULL,
  reason TEXT NULL,
  args_json LONGTEXT NULL,
  observation LONGTEXT NULL,
  error_message TEXT NULL,
  idempotency_key VARCHAR(128) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  timeout_ms BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMP NULL,
  finished_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_agent_workflow_step_task FOREIGN KEY (task_id) REFERENCES agent_workflow_task(id),
  INDEX idx_agent_workflow_step_task (task_id, iteration_no),
  INDEX idx_agent_workflow_step_status (status),
  INDEX idx_agent_workflow_step_idempotency (task_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS ai_token_usage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usage_date DATE NOT NULL,
  request_type VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  scene VARCHAR(64) NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  latency_ms BIGINT NOT NULL DEFAULT 0,
  is_estimated TINYINT(1) NOT NULL DEFAULT 1,
  is_success TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_token_usage_date (usage_date),
  INDEX idx_ai_token_usage_provider_model (provider, model_name),
  INDEX idx_ai_token_usage_type (request_type),
  INDEX idx_ai_token_usage_created_by (created_by)
);

CREATE TABLE IF NOT EXISTS ai_call_trace (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usage_date DATE NOT NULL,
  trace_id VARCHAR(64) NOT NULL,
  request_type VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  scene VARCHAR(64) NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  latency_ms BIGINT NOT NULL DEFAULT 0,
  is_estimated TINYINT(1) NOT NULL DEFAULT 1,
  is_success TINYINT(1) NOT NULL DEFAULT 1,
  http_status INT NULL,
  error_code VARCHAR(64) NULL,
  error_message TEXT NULL,
  vendor_request_id VARCHAR(128) NULL,
  knowledge_base_id BIGINT NULL,
  knowledge_document_id BIGINT NULL,
  section_id BIGINT NULL,
  ai_task_id BIGINT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  request_payload MEDIUMTEXT NULL,
  response_payload MEDIUMTEXT NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_call_trace_date (usage_date),
  INDEX idx_ai_call_trace_trace_id (trace_id),
  INDEX idx_ai_call_trace_doc (knowledge_document_id),
  INDEX idx_ai_call_trace_kb (knowledge_base_id),
  INDEX idx_ai_call_trace_success (is_success),
  INDEX idx_ai_call_trace_provider_model (provider, model_name)
);

CREATE TABLE IF NOT EXISTS knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  file_type VARCHAR(32),
  content LONGTEXT,
  visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  storage_path VARCHAR(1024) NULL,
  index_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  index_message VARCHAR(1000),
  index_progress INT NOT NULL DEFAULT 0,
  indexed_at TIMESTAMP NULL,
  index_task_id VARCHAR(64),
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_document_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'index_status'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_document ADD COLUMN index_status VARCHAR(16) NOT NULL DEFAULT ''SUCCESS''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'index_message'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_document ADD COLUMN index_message VARCHAR(1000) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'indexed_at'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_document ADD COLUMN indexed_at TIMESTAMP NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'index_task_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_document ADD COLUMN index_task_id VARCHAR(64) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_document'
    AND COLUMN_NAME = 'index_progress'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_document ADD COLUMN index_progress INT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE knowledge_document SET index_status = 'SUCCESS' WHERE index_status IS NULL;
UPDATE knowledge_document SET index_progress = CASE WHEN index_status = 'SUCCESS' THEN 100 ELSE 0 END
WHERE index_progress IS NULL OR index_progress = 0;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  knowledge_document_id BIGINT,
  chunk_index INT NOT NULL,
  section_title VARCHAR(255),
  section_path VARCHAR(1000),
  chunk_type VARCHAR(32),
  content TEXT NOT NULL,
  embedding_json LONGTEXT NOT NULL,
  embedding_dim INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_knowledge_chunk_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_knowledge_chunk_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id)
);

CREATE TABLE IF NOT EXISTS knowledge_document_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_document_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kdp_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id),
  CONSTRAINT uk_kdp_doc_user UNIQUE (knowledge_document_id, user_id)
);

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_chunk'
    AND COLUMN_NAME = 'section_title'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_chunk ADD COLUMN section_title VARCHAR(255) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_chunk'
    AND COLUMN_NAME = 'section_path'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_chunk ADD COLUMN section_path VARCHAR(1000) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_chunk'
    AND COLUMN_NAME = 'chunk_type'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_chunk ADD COLUMN chunk_type VARCHAR(32) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS knowledge_chunk_term (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  knowledge_document_id BIGINT NOT NULL,
  knowledge_chunk_id BIGINT NOT NULL,
  term_type VARCHAR(32) NOT NULL,
  term_key VARCHAR(255) NOT NULL,
  term_name VARCHAR(255) NOT NULL,
  frequency INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kct_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_kct_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id),
  CONSTRAINT fk_kct_chunk FOREIGN KEY (knowledge_chunk_id) REFERENCES knowledge_chunk(id)
);

-- -- CREATE INDEX IF NOT EXISTS idx_kct_kb_doc_type_key ON knowledge_chunk_term (knowledge_base_id, knowledge_document_id, term_type, term_key);
-- -- CREATE INDEX IF NOT EXISTS idx_kct_kb_type_key ON knowledge_chunk_term (knowledge_base_id, term_type, term_key);

CREATE TABLE IF NOT EXISTS knowledge_graph_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  knowledge_document_id BIGINT NOT NULL,
  knowledge_chunk_id BIGINT NOT NULL,
  node_key VARCHAR(255) NOT NULL,
  node_name VARCHAR(255) NOT NULL,
  node_type VARCHAR(64) NOT NULL,
  frequency INT NOT NULL,
  source VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kgn_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_kgn_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id),
  CONSTRAINT fk_kgn_chunk FOREIGN KEY (knowledge_chunk_id) REFERENCES knowledge_chunk(id)
);

CREATE TABLE IF NOT EXISTS knowledge_graph_edge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  knowledge_document_id BIGINT NOT NULL,
  knowledge_chunk_id BIGINT NOT NULL,
  source_node_key VARCHAR(255) NOT NULL,
  source_node_name VARCHAR(255) NOT NULL,
  target_node_key VARCHAR(255) NOT NULL,
  target_node_name VARCHAR(255) NOT NULL,
  relation_type VARCHAR(64) NOT NULL,
  relation_name VARCHAR(128) NOT NULL,
  frequency INT NOT NULL,
  source VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kge_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_kge_doc FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document(id),
  CONSTRAINT fk_kge_chunk FOREIGN KEY (knowledge_chunk_id) REFERENCES knowledge_chunk(id)
);

CREATE TABLE IF NOT EXISTS knowledge_base_domain_lexicon (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  category VARCHAR(64) NOT NULL,
  term VARCHAR(255) NOT NULL,
  standard_term VARCHAR(255),
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kb_lexicon_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT uk_kb_lexicon_term UNIQUE (knowledge_base_id, category, term)
);

CREATE TABLE IF NOT EXISTS exam_paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  instructions TEXT,
  total_score DECIMAL(10,2) NOT NULL,
  share_token VARCHAR(64) NULL,
  is_published TINYINT(1) NOT NULL DEFAULT 0,
  published_at TIMESTAMP NULL,
  generated_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_exam_paper_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id)
);

CREATE TABLE IF NOT EXISTS exam_question (
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

CREATE TABLE IF NOT EXISTS exam_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  submitter_id BIGINT,
  submitter_name VARCHAR(128) NULL,
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

CREATE TABLE IF NOT EXISTS section_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  structure_json LONGTEXT NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  manager_name VARCHAR(128),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

SET @app_user_department_col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'app_user'
    AND COLUMN_NAME = 'department_id'
);
SET @add_app_user_department_col := IF(
  @app_user_department_col_exists = 0,
  'ALTER TABLE app_user ADD COLUMN department_id BIGINT',
  'SELECT 1'
);
PREPARE stmt_add_app_user_department_col FROM @add_app_user_department_col;
EXECUTE stmt_add_app_user_department_col;
DEALLOCATE PREPARE stmt_add_app_user_department_col;

SET @fk_app_user_dept_exists := (
  SELECT COUNT(1)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'app_user'
    AND CONSTRAINT_NAME = 'fk_app_user_department'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @add_fk_app_user_dept := IF(
  @fk_app_user_dept_exists = 0,
  'ALTER TABLE app_user ADD CONSTRAINT fk_app_user_department FOREIGN KEY (department_id) REFERENCES department(id)',
  'SELECT 1'
);
PREPARE stmt_add_fk_app_user_dept FROM @add_fk_app_user_dept;
EXECUTE stmt_add_fk_app_user_dept;
DEALLOCATE PREPARE stmt_add_fk_app_user_dept;

SET @kb_lexicon_standard_term_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_base_domain_lexicon'
    AND COLUMN_NAME = 'standard_term'
);
SET @add_kb_lexicon_standard_term := IF(
  @kb_lexicon_standard_term_exists = 0,
  'ALTER TABLE knowledge_base_domain_lexicon ADD COLUMN standard_term VARCHAR(255)',
  'SELECT 1'
);
PREPARE stmt_add_kb_lexicon_standard_term FROM @add_kb_lexicon_standard_term;
EXECUTE stmt_add_kb_lexicon_standard_term;
DEALLOCATE PREPARE stmt_add_kb_lexicon_standard_term;

CREATE TABLE IF NOT EXISTS app_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT,
  title VARCHAR(128) NOT NULL,
  path VARCHAR(255),
  icon VARCHAR(64),
  sort_index INT NOT NULL,
  visible TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_app_menu_parent FOREIGN KEY (parent_id) REFERENCES app_menu(id)
);

CREATE TABLE IF NOT EXISTS role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES role(id),
  CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES app_menu(id),
  CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS user_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_menu_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_user_menu_menu FOREIGN KEY (menu_id) REFERENCES app_menu(id),
  CONSTRAINT uk_user_menu UNIQUE (user_id, menu_id)
);

CREATE TABLE IF NOT EXISTS domain_dictionary_pack (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  scope_type VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  description TEXT,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS domain_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 100,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS domain_category_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_category_id BIGINT NULL,
  target_category_id BIGINT NULL,
  source_category VARCHAR(64) NOT NULL,
  target_category VARCHAR(64) NOT NULL,
  relation_label VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_domain_category_relation UNIQUE (source_category, target_category, relation_label)
);

CREATE TABLE IF NOT EXISTS domain_dictionary_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pack_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  category VARCHAR(64) NOT NULL,
  term VARCHAR(255) NOT NULL,
  standard_term VARCHAR(255),
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_domain_entry_pack FOREIGN KEY (pack_id) REFERENCES domain_dictionary_pack(id),
  CONSTRAINT uk_domain_entry UNIQUE (pack_id, category, term)
);

CREATE TABLE IF NOT EXISTS knowledge_base_dictionary_pack (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_base_id BIGINT NOT NULL,
  pack_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  priority INT NOT NULL DEFAULT 100,
  created_by BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kbdp_kb FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base(id),
  CONSTRAINT fk_kbdp_pack FOREIGN KEY (pack_id) REFERENCES domain_dictionary_pack(id),
  CONSTRAINT uk_kbdp UNIQUE (knowledge_base_id, pack_id)
);

SET @fk_section_current_version_exists := (
  SELECT COUNT(1)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'section'
    AND CONSTRAINT_NAME = 'fk_section_current_version'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @add_fk_section_current_version := IF(
  @fk_section_current_version_exists = 0,
  'ALTER TABLE section ADD CONSTRAINT fk_section_current_version FOREIGN KEY (current_version_id) REFERENCES section_version(id)',
  'SELECT 1'
);
PREPARE stmt_add_fk_section_current_version FROM @add_fk_section_current_version;
EXECUTE stmt_add_fk_section_current_version;
DEALLOCATE PREPARE stmt_add_fk_section_current_version;

INSERT IGNORE INTO app_user (id, username, password_hash, real_name, dept, department_id, status)
VALUES (1, 'admin', '{noop}admin123', '管理员', '售前解决方案部', NULL, 'ACTIVE');

INSERT IGNORE INTO department (id, code, name, manager_name, status)
VALUES
  (1, 'PRESALES', '售前解决方案部', '张工', 'ACTIVE'),
  (2, 'KNOWLEDGE', '知识运营部', '李工', 'ACTIVE'),
  (3, 'MARKET', '市场培训部', '王工', 'ACTIVE');

UPDATE app_user SET department_id = 1 WHERE id = 1 AND department_id IS NULL;

INSERT IGNORE INTO role (id, code, name)
VALUES
  (1, 'ADMIN', '系统管理员'),
  (2, 'PRE_SALES', '售前工程师'),
  (3, 'MARKET', '市场人员');

INSERT IGNORE INTO user_role (user_id, role_id)
VALUES (1, 1);

INSERT IGNORE INTO app_menu (id, parent_id, title, path, icon, sort_index, visible)
VALUES
  (1, NULL, '项目管理', '/projects', 'Folder', 10, 1),
  (2, NULL, '知识中心', NULL, 'Collection', 20, 1),
  (3, 2, '知识库', '/knowledge', 'Document', 21, 1),
  (4, 2, '领域词典中心', '/domain-lexicons', 'Tickets', 22, 1),
  (5, 2, '知识图谱', '/knowledge-graph', 'Share', 23, 1),
  (6, NULL, '审核中心', '/reviews', 'Checked', 30, 1),
  (7, NULL, '基础信息管理', NULL, 'Setting', 40, 1),
  (8, 7, '部门管理', '/base/depts', 'OfficeBuilding', 41, 1),
  (9, 7, '用户管理', '/base/users', 'UserFilled', 42, 1),
  (10, 7, '角色管理', '/base/roles', 'Avatar', 43, 1),
  (11, 7, '菜单管理', '/base/menus', 'Menu', 44, 1),
  (12, NULL, '章节资产库', '/assets', 'Document', 90, 0),
  (13, NULL, '考试中心', '/exams', 'Reading', 91, 0),
  (14, NULL, 'Token 统计', '/base/ai-token-usage', 'Reading', 92, 0);

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, m.id FROM app_menu m;

INSERT IGNORE INTO role_menu (role_id, menu_id)
VALUES
  (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6),
  (3, 2), (3, 3), (3, 4), (3, 5);

INSERT IGNORE INTO domain_dictionary_pack (id, code, name, scope_type, status, description, created_by)
VALUES
  (1, 'ENV_COMMON', '环保行业词典', 'GLOBAL', 'ACTIVE', '环境监测行业通用术语', 1),
  (2, 'IT_COMMON', '通用IT架构词典', 'GLOBAL', 'ACTIVE', 'IT系统架构术语与同义词', 1);

INSERT IGNORE INTO domain_category (id, code, name, description, status, sort_order)
VALUES
  (1, 'POLLUTANT', '污染物', '气态/颗粒物/有机污染物等监测对象', 'ACTIVE', 10),
  (2, 'INSTRUMENT', '仪器设备', '监测仪、采样器、分析仪等', 'ACTIVE', 20),
  (3, 'SITE', '点位区域', '站点、断面、区域、园区等', 'ACTIVE', 30),
  (4, 'STANDARD', '标准规范', '国家/行业/地方标准与技术规范', 'ACTIVE', 40),
  (5, 'FAULT', '故障异常', '漂移、堵塞、失真、异常告警', 'ACTIVE', 50),
  (6, 'ACTION', '处置动作', '检修、清洗、标定、更换等', 'ACTIVE', 60),
  (7, 'PARAMETER', '参数指标', '浓度、温度、流速、COD等监测参数', 'ACTIVE', 70),
  (8, 'PROCESS', '工艺流程', '采样、传输、分析、治理流程', 'ACTIVE', 80),
  (9, 'COMPONENT', '组件部件', '泵、阀、探头、滤膜等', 'ACTIVE', 90),
  (10, 'CAUSE', '原因因素', '工况、环境、供电、通信等诱因', 'ACTIVE', 100);

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_dictionary_entry'
    AND COLUMN_NAME = 'category_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE domain_dictionary_entry ADD COLUMN category_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_base_domain_lexicon'
    AND COLUMN_NAME = 'category_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE knowledge_base_domain_lexicon ADD COLUMN category_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_category_relation'
    AND COLUMN_NAME = 'source_category_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE domain_category_relation ADD COLUMN source_category_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_category_relation'
    AND COLUMN_NAME = 'target_category_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE domain_category_relation ADD COLUMN target_category_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE domain_dictionary_entry e
JOIN domain_category c ON UPPER(TRIM(e.category)) = c.code
SET e.category_id = c.id
WHERE e.category_id IS NULL;

UPDATE knowledge_base_domain_lexicon e
JOIN domain_category c ON UPPER(TRIM(e.category)) = c.code
SET e.category_id = c.id
WHERE e.category_id IS NULL;

UPDATE domain_category_relation r
JOIN domain_category sc ON UPPER(TRIM(r.source_category)) = sc.code
JOIN domain_category tc ON UPPER(TRIM(r.target_category)) = tc.code
SET r.source_category_id = sc.id,
    r.target_category_id = tc.id
WHERE r.source_category_id IS NULL OR r.target_category_id IS NULL;

SET @fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_dictionary_entry'
    AND CONSTRAINT_NAME = 'fk_domain_entry_category'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE domain_dictionary_entry ADD CONSTRAINT fk_domain_entry_category FOREIGN KEY (category_id) REFERENCES domain_category(id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'knowledge_base_domain_lexicon'
    AND CONSTRAINT_NAME = 'fk_kb_lexicon_category'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE knowledge_base_domain_lexicon ADD CONSTRAINT fk_kb_lexicon_category FOREIGN KEY (category_id) REFERENCES domain_category(id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_category_relation'
    AND CONSTRAINT_NAME = 'fk_domain_relation_source'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE domain_category_relation ADD CONSTRAINT fk_domain_relation_source FOREIGN KEY (source_category_id) REFERENCES domain_category(id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_category_relation'
    AND CONSTRAINT_NAME = 'fk_domain_relation_target'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE domain_category_relation ADD CONSTRAINT fk_domain_relation_target FOREIGN KEY (target_category_id) REFERENCES domain_category(id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO domain_category_relation (id, source_category, target_category, relation_label, enabled, created_by)
VALUES
  (1, 'INSTRUMENT', 'POLLUTANT', '监测', 1, 1),
  (2, 'INSTRUMENT', 'SITE', '部署于', 1, 1),
  (3, 'FAULT', 'COMPONENT', '发生于', 1, 1),
  (4, 'FAULT', 'CAUSE', '由...导致', 1, 1),
  (5, 'ACTION', 'FAULT', '处置', 1, 1),
  (6, 'PROCESS', 'STANDARD', '遵循', 1, 1),
  (7, 'PARAMETER', 'INSTRUMENT', '配置于', 1, 1);

UPDATE domain_category_relation r
JOIN domain_category sc ON UPPER(TRIM(r.source_category)) = sc.code
JOIN domain_category tc ON UPPER(TRIM(r.target_category)) = tc.code
SET r.source_category_id = sc.id,
    r.target_category_id = tc.id
WHERE r.source_category_id IS NULL OR r.target_category_id IS NULL;

SET @uk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_dictionary_entry'
    AND CONSTRAINT_NAME = 'uk_domain_entry_ref'
    AND CONSTRAINT_TYPE = 'UNIQUE'
);
SET @sql = IF(@uk_exists = 0,
  'ALTER TABLE domain_dictionary_entry ADD CONSTRAINT uk_domain_entry_ref UNIQUE (pack_id, category_id, term)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @uk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'domain_category_relation'
    AND CONSTRAINT_NAME = 'uk_domain_category_relation_ref'
    AND CONSTRAINT_TYPE = 'UNIQUE'
);
SET @sql = IF(@uk_exists = 0,
  'ALTER TABLE domain_category_relation ADD CONSTRAINT uk_domain_category_relation_ref UNIQUE (source_category_id, target_category_id, relation_label)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exam_share_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'exam_paper'
    AND INDEX_NAME = 'uk_exam_paper_share_token'
);
SET @create_idx_exam_share := IF(
  @idx_exam_share_exists = 0,
  'CREATE UNIQUE INDEX uk_exam_paper_share_token ON exam_paper(share_token)',
  'SELECT 1'
);
PREPARE stmt_create_idx_exam_share FROM @create_idx_exam_share;
EXECUTE stmt_create_idx_exam_share;
DEALLOCATE PREPARE stmt_create_idx_exam_share;
