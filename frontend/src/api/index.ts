import http from './http'

export const api = {
  login(data: { username: string; password: string }) {
    return http.post('/auth/login', data)
  },
  listProjects() {
    return http.get('/projects')
  },
  createProject(data: any) {
    return http.post('/projects', data)
  },
  getProject(id: number) {
    return http.get(`/projects/${id}`)
  },
  updateProject(id: number, data: any) {
    return http.put(`/projects/${id}`, data)
  },
  listDocuments(projectId: number) {
    return http.get(`/projects/${projectId}/documents`)
  },
  createDocument(projectId: number, data: any) {
    return http.post(`/projects/${projectId}/documents`, data)
  },
  getDocument(id: number) {
    return http.get(`/documents/${id}`)
  },
  listSectionTree(documentId: number) {
    return http.get(`/documents/${documentId}/sections/tree`)
  },
  createSection(documentId: number, data: any) {
    return http.post(`/documents/${documentId}/sections`, data)
  },
  updateSection(id: number, data: any) {
    return http.put(`/sections/${id}`, data)
  },
  moveSection(id: number, data: any) {
    return http.post(`/sections/${id}/move`, data)
  },
  deleteSection(id: number) {
    return http.delete(`/sections/${id}`)
  },
  lockSection(id: number) {
    return http.post(`/sections/${id}/lock`)
  },
  unlockSection(id: number) {
    return http.post(`/sections/${id}/unlock`)
  },
  listVersions(sectionId: number) {
    return http.get(`/sections/${sectionId}/versions`)
  },
  getVersion(sectionId: number, versionId: number) {
    return http.get(`/sections/${sectionId}/versions/${versionId}`)
  },
  createVersion(sectionId: number, data: any) {
    return http.post(`/sections/${sectionId}/versions`, data)
  },
  listSectionTemplates() {
    return http.get('/section-templates')
  },
  createSectionTemplateFromDocument(data: any) {
    return http.post('/section-templates/from-document', data)
  },
  applySectionTemplate(documentId: number, templateId: number, data: any = {}) {
    return http.post(`/documents/${documentId}/section-templates/${templateId}/apply`, data)
  },
  createAsset(sectionId: number, data: any) {
    return http.post(`/sections/${sectionId}/asset`, data)
  },
  searchAssets(params: any) {
    return http.get('/assets/search', { params })
  },
  reuseSection(parentSectionId: number, data: any) {
    return http.post(`/sections/${parentSectionId}/reuse`, data)
  },
  listReuseTrace(sectionId: number) {
    return http.get(`/sections/${sectionId}/reuse-trace`)
  },
  submitReview(sectionId: number, data: any) {
    return http.post(`/sections/${sectionId}/review/submit`, data)
  },
  approveReview(sectionId: number, data: any) {
    return http.post(`/sections/${sectionId}/review/approve`, data)
  },
  rejectReview(sectionId: number, data: any) {
    return http.post(`/sections/${sectionId}/review/reject`, data)
  },
  listReviews(sectionId: number) {
    return http.get(`/sections/${sectionId}/reviews`)
  },
  aiRewrite(data: any) {
    return http.post('/ai/rewrite', data)
  },
  aiAsk(data: any) {
    return http.post('/ai/ask', data)
  },
  getAiTask(id: number) {
    return http.get(`/ai/tasks/${id}`)
  },
  exportDocument(documentId: number, data: any) {
    return http.post(`/documents/${documentId}/export`, data)
  },
  listExports(documentId: number) {
    return http.get(`/documents/${documentId}/exports`)
  },
  downloadExport(exportId: number) {
    return http.get(`/exports/${exportId}/download`, { responseType: 'blob' })
  },
  uploadEditorImage(formData: FormData) {
    return http.post('/files/images', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  createKnowledgeBase(data: any) {
    return http.post('/knowledge-bases', data)
  },
  listKnowledgeBases() {
    return http.get('/knowledge-bases')
  },
  deleteKnowledgeBase(kbId: number) {
    return http.delete(`/knowledge-bases/${kbId}`)
  },
  uploadKnowledgeFile(kbId: number, formData: FormData) {
    return http.post(`/knowledge-bases/${kbId}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  reindexKnowledgeBase(kbId: number) {
    return http.post(`/knowledge-bases/${kbId}/reindex`)
  },
  listDomainLexicons(knowledgeBaseId: number) {
    return http.get(`/domain-lexicons`, { params: { knowledgeBaseId } })
  },
  upsertDomainLexicon(data: any) {
    return http.post(`/domain-lexicons`, data)
  },
  updateDomainLexicon(lexiconId: number, data: any) {
    return http.put(`/domain-lexicons/${lexiconId}`, data)
  },
  deleteDomainLexicon(lexiconId: number) {
    return http.delete(`/domain-lexicons/${lexiconId}`)
  },
  addKnowledgeContent(kbId: number, data: any) {
    return http.post(`/knowledge-bases/${kbId}/contents`, data)
  },
  listKnowledgeDocuments(kbId: number) {
    return http.get(`/knowledge-bases/${kbId}/documents`)
  },
  getKnowledgeGraph(kbId: number) {
    return http.get(`/knowledge-bases/${kbId}/graph`)
  },
  getKnowledgeGraphNodeDetail(kbId: number, nodeId: string) {
    return http.get(`/knowledge-bases/${kbId}/graph/node-detail`, { params: { nodeId } })
  },
  updateKnowledgeVisibility(kbId: number, documentId: number, data: any) {
    return http.post(`/knowledge-bases/${kbId}/documents/${documentId}/visibility`, data)
  },
  grantKnowledgeAccess(kbId: number, documentId: number, data: any) {
    return http.post(`/knowledge-bases/${kbId}/documents/${documentId}/permissions`, data)
  },
  searchKnowledge(kbId: number, data: any) {
    return http.post(`/knowledge-bases/${kbId}/search`, data)
  },
  generateExam(data: any) {
    return http.post('/exams/generate', data)
  },
  listExams(knowledgeBaseId: number) {
    return http.get('/exams', { params: { knowledgeBaseId } })
  },
  getExam(paperId: number) {
    return http.get(`/exams/${paperId}`)
  },
  publishExam(paperId: number) {
    return http.post(`/exams/${paperId}/publish`)
  },
  deleteExam(paperId: number) {
    return http.delete(`/exams/${paperId}`)
  },
  submitExam(paperId: number, data: any) {
    return http.post(`/exams/${paperId}/submit`, data)
  },
  listExamSubmissions(paperId: number) {
    return http.get(`/exams/${paperId}/submissions`)
  },
  getSubmission(submissionId: number) {
    return http.get(`/exams/submissions/${submissionId}`)
  },
  getPublicExam(shareToken: string) {
    return http.get(`/public/exams/${shareToken}`)
  },
  submitPublicExam(shareToken: string, data: any) {
    return http.post(`/public/exams/${shareToken}/submit`, data)
  },
  listPublicLeaderboard(shareToken: string) {
    return http.get(`/public/exams/${shareToken}/leaderboard`)
  },
  getPublicSubmission(shareToken: string, submissionId: number) {
    return http.get(`/public/exams/${shareToken}/submissions/${submissionId}`)
  }
}
