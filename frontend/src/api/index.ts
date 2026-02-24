import http from './http'

export const api = {
  login(data: { username: string; password: string }) {
    return http.post('/auth/login', data)
  },
  register(data: { username: string; password: string }) {
    return http.post('/auth/register', data)
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
  deleteDocument(id: number) {
    return http.delete(`/documents/${id}`)
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
  listSectionChunkRefs(sectionId: number) {
    return http.get(`/sections/${sectionId}/chunk-refs`)
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
  aiAutoWriteDocument(data: any) {
    return http.post('/ai/document-auto-write', data)
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
  deleteExport(exportId: number) {
    return http.delete(`/exports/${exportId}`)
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
  listDictionaryPacks() {
    return http.get('/domain-lexicons/packs')
  },
  createDictionaryPack(data: any) {
    return http.post('/domain-lexicons/packs', data)
  },
  updateDictionaryPack(packId: number, data: any) {
    return http.put(`/domain-lexicons/packs/${packId}`, data)
  },
  deleteDictionaryPack(packId: number) {
    return http.delete(`/domain-lexicons/packs/${packId}`)
  },
  listDictionaryEntries(packId: number) {
    return http.get(`/domain-lexicons/packs/${packId}/entries`)
  },
  upsertDictionaryEntry(packId: number, data: any) {
    return http.post(`/domain-lexicons/packs/${packId}/entries`, data)
  },
  updateDictionaryEntry(entryId: number, data: any) {
    return http.put(`/domain-lexicons/entries/${entryId}`, data)
  },
  deleteDictionaryEntry(entryId: number) {
    return http.delete(`/domain-lexicons/entries/${entryId}`)
  },
  batchUpsertDictionaryEntries(packId: number, data: any) {
    return http.post(`/domain-lexicons/packs/${packId}/entries/batch-upsert`, data)
  },
  listKnowledgeBaseDictionaryPacks(kbId: number) {
    return http.get(`/domain-lexicons/knowledge-bases/${kbId}/packs`)
  },
  bindKnowledgeBaseDictionaryPack(kbId: number, packId: number, data: any = {}) {
    return http.post(`/domain-lexicons/knowledge-bases/${kbId}/packs/${packId}`, data)
  },
  unbindKnowledgeBaseDictionaryPack(kbId: number, packId: number) {
    return http.delete(`/domain-lexicons/knowledge-bases/${kbId}/packs/${packId}`)
  },
  addKnowledgeContent(kbId: number, data: any) {
    return http.post(`/knowledge-bases/${kbId}/contents`, data)
  },
  listKnowledgeDocuments(kbId: number) {
    return http.get(`/knowledge-bases/${kbId}/documents`)
  },
  listKnowledgeChunks(kbId: number) {
    return http.get(`/knowledge-bases/${kbId}/chunks`)
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
  },
  listDepartments() {
    return http.get('/base/departments')
  },
  createDepartment(data: any) {
    return http.post('/base/departments', data)
  },
  updateDepartment(id: number, data: any) {
    return http.put(`/base/departments/${id}`, data)
  },
  deleteDepartment(id: number) {
    return http.delete(`/base/departments/${id}`)
  },
  listMenus(params?: any) {
    return http.get('/base/menus', { params })
  },
  createMenu(data: any) {
    return http.post('/base/menus', data)
  },
  updateMenu(id: number, data: any) {
    return http.put(`/base/menus/${id}`, data)
  },
  deleteMenu(id: number) {
    return http.delete(`/base/menus/${id}`)
  },
  listRoles() {
    return http.get('/base/roles')
  },
  createRole(data: any) {
    return http.post('/base/roles', data)
  },
  updateRole(id: number, data: any) {
    return http.put(`/base/roles/${id}`, data)
  },
  deleteRole(id: number) {
    return http.delete(`/base/roles/${id}`)
  },
  listUsersByBase() {
    return http.get('/base/users')
  },
  createBaseUser(data: any) {
    return http.post('/base/users', data)
  },
  updateBaseUser(id: number, data: any) {
    return http.put(`/base/users/${id}`, data)
  },
  deleteBaseUser(id: number) {
    return http.delete(`/base/users/${id}`)
  },
  getCurrentUserMenus() {
    return http.get('/base/current-user-menus')
  }
}
