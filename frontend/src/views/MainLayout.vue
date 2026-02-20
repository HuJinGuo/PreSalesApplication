<template>
  <el-container class="drive-layout">
    <el-aside width="264px" class="drive-sidebar">
      <div class="drive-logo">
        <div class="logo-mark">
          <el-icon><Files /></el-icon>
        </div>
        <div>
          <div class="logo-title">Bid Drive</div>
          <div class="logo-sub">环境监测知识协作</div>
        </div>
      </div>

      <div class="quick-create">
        <el-button type="primary" round size="large" @click="router.push('/projects')">
          <el-icon><Plus /></el-icon>
          新建项目
        </el-button>
      </div>

      <el-menu class="drive-menu" :default-active="activePath" router>
        <el-menu-item index="/projects">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/domain-lexicons">
          <el-icon><Tickets /></el-icon>
          <span>领域词典</span>
        </el-menu-item>
        <el-menu-item index="/knowledge-graph">
          <el-icon><Share /></el-icon>
          <span>知识图谱</span>
        </el-menu-item>
        <el-menu-item index="/assets">
          <el-icon><Document /></el-icon>
          <span>章节资产库</span>
        </el-menu-item>
        <el-menu-item index="/exams">
          <el-icon><Reading /></el-icon>
          <span>考试中心</span>
        </el-menu-item>
        <el-menu-item index="/reviews">
          <el-icon><Checked /></el-icon>
          <span>审核中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="drive-main-wrap">
      <el-header class="drive-topbar">
        <div class="topbar-left">
          <div class="workspace-title">售前招标文档协作平台</div>
          <div class="top-search" @click="openAssistant">
            <el-icon><Search /></el-icon>
            <input
              v-model="assistantQuery"
              class="top-search-input"
              placeholder="搜索项目、文档、知识..."
              @click.stop
              @keyup.enter="submitAssistantQuery"
            />
            <el-button text type="primary" @click.stop="submitAssistantQuery">AI问答</el-button>
          </div>
        </div>
        <div class="topbar-right">
          <div class="user-chip">
            <el-icon><User /></el-icon>
            <span>{{ auth.username }}</span>
          </div>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="drive-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="assistantVisible" title="AI 知识助手" width="980px" top="5vh" class="assistant-dialog">
    <div class="assistant-panel">
      <div class="assistant-form card-lite">
        <div class="assistant-form-top">
          <el-select v-model="assistantKbId" clearable placeholder="全部知识库" style="width: 220px">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <div class="assistant-tip">`Ctrl + Enter` 快速提问</div>
        </div>
        <div class="assistant-form-bottom">
          <el-input
            v-model="assistantQuery"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="输入你的问题，AI会自动检索知识库后再回答"
            @keyup.enter.ctrl.exact.prevent="submitAssistantQuery"
          />
          <el-button type="primary" :loading="assistantLoading" @click="submitAssistantQuery">
            检索并回答
          </el-button>
        </div>
      </div>

      <div class="assistant-answer card-lite" v-loading="assistantLoading">
        <div class="assistant-answer-title">回答</div>
        <div
          v-if="assistantAnswer"
          class="assistant-answer-content markdown-body"
          v-html="assistantAnswerHtml"
        ></div>
        <div v-else class="assistant-answer-empty">还没有回答，请先提问。</div>
      </div>

      <div class="assistant-citation card-lite">
        <div class="assistant-answer-title">知识引用</div>
        <el-table :data="assistantCitations" size="small" max-height="280">
          <el-table-column type="expand" width="56">
            <template #default="scope">
              <div class="citation-expand">
                <div class="citation-expand-title">片段 Markdown 预览</div>
                <div
                  class="citation-markdown markdown-body"
                  v-html="renderMarkdown(scope.row.snippet || '')"
                ></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="knowledgeBaseName" label="知识库" width="120" />
          <el-table-column prop="documentTitle" label="文档" width="220" />
          <el-table-column prop="score" label="相似度" width="90">
            <template #default="scope">{{ Number(scope.row.score || 0).toFixed(3) }}</template>
          </el-table-column>
          <el-table-column label="片段摘要">
            <template #default="scope">
              <div class="citation-snippet">{{ scope.row.snippet }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { Checked, Collection, Document, Files, Folder, Plus, Reading, Search, Share, Tickets, User } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const assistantVisible = ref(false)
const assistantQuery = ref('')
const assistantKbId = ref<number>()
const assistantLoading = ref(false)
const assistantAnswer = ref('')
const assistantCitations = ref<any[]>([])
const knowledgeBases = ref<any[]>([])
const assistantAnswerHtml = computed(() => {
  if (!assistantAnswer.value) return ''
  const raw = marked.parse(assistantAnswer.value, { gfm: true, breaks: true, async: false }) as string
  return DOMPurify.sanitize(raw)
})
const renderMarkdown = (input: string) => {
  if (!input) return ''
  const raw = marked.parse(input, { gfm: true, breaks: true, async: false }) as string
  return DOMPurify.sanitize(raw)
}

const activePath = computed(() => route.path)

const logout = () => {
  auth.clear()
  router.push('/login')
}

const loadKnowledgeBases = async () => {
  if (knowledgeBases.value.length > 0) return
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data || []
}

const openAssistant = async () => {
  assistantVisible.value = true
  await loadKnowledgeBases()
}

const submitAssistantQuery = async () => {
  if (!assistantQuery.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  assistantVisible.value = true
  assistantLoading.value = true
  try {
    await loadKnowledgeBases()
    const { data } = await api.aiAsk({
      query: assistantQuery.value,
      knowledgeBaseId: assistantKbId.value,
      topK: 8,
      minScore: 0.03,
      rerank: false
    })
    assistantAnswer.value = data.answer || ''
    assistantCitations.value = data.citations || []
  } finally {
    assistantLoading.value = false
  }
}
</script>

<style scoped>
.drive-layout {
  min-height: 100vh;
  background: var(--app-bg);
}

.drive-sidebar {
  background: #f8fafd;
  border-right: 1px solid #e7ebf3;
  padding: 16px 10px;
}

.drive-logo {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 12px 14px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a73e8, #4dabf7);
  color: #fff;
}

.logo-title {
  font-weight: 700;
  color: #1f2a3d;
}

.logo-sub {
  font-size: 12px;
  color: #6b7a90;
}

.quick-create {
  padding: 8px 12px 12px;
}

.drive-menu {
  border-right: none;
  background: transparent;
}

.drive-main-wrap {
  background: var(--app-bg);
}

.drive-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  background: rgba(250, 252, 255, 0.86);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid #e7ebf3;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.workspace-title {
  color: #1f2a3d;
  font-weight: 600;
  min-width: 210px;
}

.top-search {
  min-width: 460px;
  border-radius: 999px;
  background: #ecf2fb;
  color: #5f6e84;
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 14px;
  cursor: pointer;
}

.top-search-input {
  flex: 1;
  min-width: 260px;
  border: none;
  outline: none;
  background: transparent;
  color: #344259;
  font-size: 14px;
}

.top-search-input::placeholder {
  color: #71829b;
}

.topbar-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1f2a3d;
}

.drive-main {
  padding: 18px;
}

.assistant-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 82vh;
  overflow: auto;
  padding-right: 4px;
}

.card-lite {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff, #f9fbff);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  padding: 12px;
}

.assistant-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-form-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-form-bottom {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-tip {
  color: #6b7a90;
  font-size: 12px;
}

.assistant-answer {
  min-height: 200px;
  display: flex;
  flex-direction: column;
}

.assistant-answer-title {
  font-weight: 600;
  color: #2a3850;
  margin-bottom: 6px;
}

.assistant-answer-content {
  color: #334155;
  line-height: 1.7;
  overflow: auto;
  max-height: 44vh;
  min-height: 140px;
  padding-right: 6px;
}

.assistant-answer-empty {
  color: #64748b;
  font-size: 13px;
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 10px 0 8px;
  color: #1f2a3d;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 18px;
}

.markdown-body :deep(code) {
  background: #eef2f7;
  border-radius: 4px;
  padding: 1px 4px;
  color: #1e293b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.markdown-body :deep(pre) {
  background: #0b1736;
  color: #e6edf7;
  border-radius: 10px;
  padding: 12px 14px;
  overflow-x: auto;
  line-height: 1.6;
}

/* 代码块内 code 不能复用行内 code 的灰底样式，否则会出现“每行一条灰块” */
.markdown-body :deep(pre code) {
  display: block;
  background: transparent;
  color: inherit;
  padding: 0;
  border-radius: 0;
  white-space: pre;
  font-size: 13px;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #d8e0ec;
  padding: 6px 8px;
  text-align: left;
  vertical-align: top;
}

.markdown-body :deep(th) {
  background: #f3f6fb;
}

.markdown-body :deep(br) {
  line-height: 1.8;
}

.assistant-citation {
  padding-top: 10px;
}

.citation-expand {
  padding: 6px 8px 10px;
  background: #f8fafc;
  border-radius: 8px;
}

.citation-expand-title {
  color: #475569;
  font-size: 12px;
  margin-bottom: 6px;
}

.citation-markdown {
  color: #334155;
  line-height: 1.65;
}

.citation-snippet {
  color: #475569;
  line-height: 1.5;
  max-height: 42px;
  overflow: hidden;
}

:deep(.assistant-dialog .el-dialog__header) {
  padding-bottom: 10px;
  border-bottom: 1px solid #edf2f7;
}

:deep(.assistant-dialog .el-dialog__body) {
  padding-top: 14px;
}

:deep(.el-menu-item) {
  border-radius: 10px;
  margin: 2px 8px;
  color: #32435d;
}

:deep(.el-menu-item.is-active) {
  background: #e8f0fe;
  color: #1a73e8;
}
</style>
