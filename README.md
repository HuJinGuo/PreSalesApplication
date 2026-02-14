# 售前招标文档协作平台

## 目录结构
- `backend` Spring Boot + Java 17
- `frontend` Vue 3 + TypeScript + Vite + Pinia + Element Plus

## 后端启动
1. 创建数据库 `ai_smart`
2. 修改 `backend/src/main/resources/application.yml` 数据库账号
3. 启动后端：

```bash
cd /Users/yuejin/33workspace/codex/bid-doc-collab/backend
mvn spring-boot:run
```

默认账号：`admin` / `admin123`

Swagger: `http://localhost:8080/swagger-ui/index.html`

## 前端启动
```bash
cd /Users/yuejin/33workspace/codex/bid-doc-collab/frontend
npm install
npm run dev
```

## 关键说明
- 章节内容存储在 `section_version`，`section.current_version_id` 指向当前版本
- 章节复用可追溯：`section_reuse_trace`
- AI 统一通过 `AiClient(chat + embedding)`，支持 `MOCK/ALIBABA/BAIDU/SELF_HOSTED`
- 导出支持 `docx/pdf`，生成文件存储在 `app.export.base-dir`
- 知识库支持 `docx/pdf/txt/md` 上传解析，切片后向量化写入 MySQL 的 `knowledge_chunk`
- 知识检索支持召回过滤（`minScore`）、候选集控制（`candidateTopK`）与 AI 重排（`rerank`）
- 知识文档支持权限隔离：`PRIVATE/PUBLIC` + 指定用户授权
- 知识库支持知识图谱展示（文档节点、关键词节点、包含关系与共现关系）
- 考试中心支持按知识库 AI 出题（单选/判断/填空/简答）与 AI 判卷
- 试卷支持发布独立外链，市场人员可匿名访问 `/exam-link/{shareToken}` 答题
- 重复提交策略可配置：`app.exam.repeat-strategy=ALL|HIGHEST|LATEST`

## AI 供应商配置
在 `backend/src/main/resources/application.yml` 的 `app.ai` 下配置：
- `provider`: `MOCK` | `ALIBABA` | `BAIDU` | `SELF_HOSTED`
- `chat-model`: 对话模型名
- `embedding-model`: 向量模型名
- `alibaba/base-url/api-key`
- `baidu/auth-url/chat-url/embedding-url/api-key/secret-key`
- `self-hosted/base-url/api-key`（OpenAI 兼容协议）

## 新增接口
- 知识库：
- `POST /api/knowledge-bases`
- `GET /api/knowledge-bases`
- `POST /api/knowledge-bases/{kbId}/upload` (`multipart/form-data`)
- `POST /api/knowledge-bases/{kbId}/contents`
- `GET /api/knowledge-bases/{kbId}/documents`
- `POST /api/knowledge-bases/{kbId}/documents/{documentId}/visibility`
- `POST /api/knowledge-bases/{kbId}/documents/{documentId}/permissions`
- `POST /api/knowledge-bases/{kbId}/search`
- `GET /api/knowledge-bases/{kbId}/graph`
- 考试（后台）：
- `POST /api/exams/generate`
- `GET /api/exams?knowledgeBaseId=xx`
- `GET /api/exams/{paperId}`
- `POST /api/exams/{paperId}/publish`
- `POST /api/exams/{paperId}/submit`
- `GET /api/exams/{paperId}/submissions`
- `GET /api/exams/submissions/{submissionId}`
- 考试（公开链接）：
- `GET /api/public/exams/{shareToken}`
- `POST /api/public/exams/{shareToken}/submit`
- `GET /api/public/exams/{shareToken}/leaderboard`
- `GET /api/public/exams/{shareToken}/submissions/{submissionId}`
