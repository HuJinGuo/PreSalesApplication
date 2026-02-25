<template>
  <div class="page editor">
    <div class="card header">
      <div class="header-title">文档编辑：{{ document?.name }}</div>
      <div class="actions">
        <el-button @click="back">返回项目</el-button>
        <el-button type="primary" @click="refresh">刷新</el-button>
      </div>
    </div>

    <div class="editor-grid">
      <div class="card tree">
        <div class="tree-header">
          <span>章节结构</span>
          <div class="tree-tools">
            <el-button size="small" @click="showApplyTemplate = true">套用模板</el-button>
            <el-button size="small" @click="showSaveTemplate = true">保存为模板</el-button>
            <el-button size="small" @click="showCreateSection = true">新增章节</el-button>
          </div>
        </div>
        <el-tree
          :data="sectionTree"
          node-key="id"
          :props="{ label: 'title', children: 'children' }"
          highlight-current
          default-expand-all
          @node-click="handleSelect"
        >
          <template #default="{ data }">
            <div class="tree-node-row">
              <span class="tree-node-title">{{ data.title }}</span>
              <div class="tree-node-actions" @click.stop>
                <el-tooltip content="新增子章节" placement="top">
                  <el-button link type="primary" size="small" :icon="CirclePlus" @click="createChildSection(data)" />
                </el-tooltip>
                <el-tooltip content="新增同级章节" placement="top">
                  <el-button link type="primary" size="small" :icon="Operation" @click="createSiblingSection(data)" />
                </el-tooltip>
                <el-tooltip content="删除章节" placement="top">
                  <el-button link type="danger" size="small" :icon="Delete" @click="deleteSectionNode(data)" />
                </el-tooltip>
              </div>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="card content" v-if="currentSection">
        <div class="content-header">
          <div>
            <div class="center-title-row">
              <el-input
                v-if="centerRenameEditing"
                ref="centerRenameInputRef"
                v-model="centerRenameTitle"
                size="small"
                class="center-title-input"
                @keyup.enter="commitCenterRename"
                @keyup.esc="cancelCenterRename"
                @blur="commitCenterRename"
              />
              <div v-else class="header-title center-title" @click="startCenterRename">
                {{ currentSection.title }}
              </div>
              <el-button
                v-if="!centerRenameEditing"
                link
                type="primary"
                size="small"
                @click="startCenterRename"
              >
                重命名
              </el-button>
            </div>
            <div class="status">
              状态：{{ currentSection.status }} | 字数：{{ textLength }} | {{ saveStatusText }}
            </div>
          </div>
          <div class="actions">
            <el-button size="small" @click="lockSection">锁定</el-button>
            <el-button size="small" @click="unlockSection">解锁</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveCurrentSection">保存（Ctrl+S）</el-button>
          </div>
        </div>

        <div ref="editorShellRef" class="editor-shell" @click="handleEditorShellClick">
          <div class="footer-actions footer-actions-top">
            <!-- <el-button @click="openAssetDialog">沉淀为资产</el-button> -->
            <el-button type="success" @click="aiRewrite">AI改写</el-button>
            <el-button type="primary" @click="openAutoWriteDialog">AI自动编写全文</el-button>
            <el-button v-if="autoWriteTaskId" @click="showAutoWriteProgressDialog = true">查看编写进度</el-button>
            <!-- <el-button type="warning" @click="submitReview">提交审核</el-button> -->
          </div>
          <Toolbar class="editor-toolbar" :editor="editorRef" :default-config="toolbarConfig" mode="default" />
          <Editor
            v-model="content"
            class="editor-main"
            :default-config="editorConfig"
            mode="default"
            @on-created="handleEditorCreated"
          />
        </div>
        <div class="editor-hint">
          支持：标题、表格、列表、图片上传/粘贴；在右侧「图片属性」面板可调整宽度、对齐方式与说明。
        </div>

      </div>
      <div class="card content content-empty" v-else>
        <el-empty description="请先在左侧选择一个章节开始编辑" :image-size="90" />
      </div>

      <div class="card versions" v-if="currentSection">
        <el-tabs v-model="rightTab">
          <el-tab-pane label="图片属性" name="image">
            <div class="image-panel">
              <div class="image-list-title">当前章节图片（{{ images.length }}）</div>
              <div class="image-list">
                <div
                  v-for="img in images"
                  :key="img.index"
                  class="image-item"
                  :class="{ active: img.index === selectedImageIndex }"
                  @click="selectImageByIndex(img.index)"
                >
                  <img :src="img.src" />
                  <div class="image-item-meta">
                    <div class="one-line">#{{ img.index + 1 }} {{ img.caption || '未命名图片' }}</div>
                    <div class="one-line subtle">{{ img.width }}px / {{ img.align }}</div>
                  </div>
                </div>
              </div>

              <el-empty v-if="!selectedImage" description="点击编辑器中的图片或在此列表中选择后可编辑属性" :image-size="68" />
              <el-form v-else label-width="70px" class="image-form">
                <el-form-item label="宽度">
                  <el-input-number v-model="imageForm.width" :min="80" :max="1200" :step="10" />
                </el-form-item>

                <el-form-item label="对齐">
                  <el-radio-group v-model="imageForm.align">
                    <el-radio-button label="left">左</el-radio-button>
                    <el-radio-button label="center">中</el-radio-button>
                    <el-radio-button label="right">右</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="说明">
                  <el-input v-model="imageForm.caption" placeholder="用于图注和导出说明" />
                </el-form-item>
                <el-form-item label="引用">
                  <el-input v-model="imageForm.src" disabled />
                </el-form-item>
                <el-alert title="属性变更会实时应用到编辑区" type="info" :closable="false" />
                <el-form-item label="导出标记">
                  <el-input :value="imageMarkerPreview" readonly />
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
          <el-tab-pane label="引用片段" name="citation">
            <div class="image-panel">
              <div class="image-list-title">正文分段引用（{{ citationRows.length }} 段）</div>
              <el-empty v-if="!citationRows.length" description="当前章节暂无引用片段" :image-size="68" />
              <div v-else class="citation-card-list">
                <div v-for="row in citationRows" :key="row.paragraphIndex" class="citation-card">
                  <div class="citation-card-head">
                    <span class="citation-index">片段 {{ row.paragraphIndex }}</span>
                    <el-space wrap>
                      <el-tag v-for="id in row.chunkIds" :key="`${row.paragraphIndex}-${id}`" size="small" type="info">
                        Chunk #{{ id }}
                      </el-tag>
                    </el-space>
                  </div>
                  <div class="citation-content">{{ row.context || '（无摘要）' }}</div>
                </div>
              </div>
              <!-- <div class="editor-hint" style="margin-top: 8px;">
                引用来自后端持久化表 <code>section_chunk_ref</code>，不依赖正文中的标记文本。
              </div> -->
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <div class="card versions content-empty" v-else>
        <el-empty description="选择章节后可查看图片属性与引用片段" :image-size="80" />
      </div>
    </div>

    <el-dialog v-model="showCreateSection" title="新增章节" width="420px">
      <el-form :model="sectionForm">
        <el-form-item label="标题">
          <el-input v-model="sectionForm.title" />
        </el-form-item>
        <el-form-item label="层级">
          <el-input-number v-model="sectionForm.level" :min="1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="sectionForm.sortIndex" :min="1" />
        </el-form-item>
        <el-form-item label="父章节">
          <el-input-number v-model="sectionForm.parentId" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateSection = false">取消</el-button>
        <el-button type="primary" @click="createSection">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAssetDialog" title="沉淀为资产" width="420px">
      <el-form :model="assetForm">
        <el-form-item label="版本ID">
          <el-input v-model="assetForm.versionId" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="assetForm.industryTag" />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input v-model="assetForm.scopeTag" />
        </el-form-item>
        <el-form-item label="是否中标">
          <el-switch v-model="assetForm.isWinning" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="assetForm.keywords" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAssetDialog = false">取消</el-button>
        <el-button type="primary" @click="createAsset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showApplyTemplate" title="套用章节模板" width="520px">
      <el-form :model="templateApplyForm">
        <el-form-item label="模板">
          <el-select v-model="templateApplyForm.templateId" style="width: 100%" placeholder="选择模板">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApplyTemplate = false">取消</el-button>
        <el-button type="primary" @click="applyTemplate">套用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSaveTemplate" title="保存章节结构为模板" width="520px">
      <el-form :model="templateSaveForm">
        <el-form-item label="模板名称">
          <el-input v-model="templateSaveForm.name" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="templateSaveForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveTemplate = false">取消</el-button>
        <el-button type="primary" @click="saveAsTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAutoWriteDialog" title="AI自动编写全文" width="560px">
      <el-form :model="autoWriteForm" label-width="120px">
        <el-form-item label="知识库">
          <el-select v-model="autoWriteForm.knowledgeBaseId" style="width: 100%" clearable placeholder="可不选（仅按结构生成）">
            <el-option v-for="kb in autoWriteKnowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖已有内容">
          <el-switch v-model="autoWriteForm.overwriteExisting" />
        </el-form-item>
        <el-form-item label="项目补充参数">
          <el-input
            v-model="autoWriteForm.projectParams"
            type="textarea"
            :rows="5"
            placeholder="可填写项目背景、客户要求、交付边界等（JSON/文本均可）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAutoWriteDialog = false">取消</el-button>
        <el-button type="primary" :loading="autoWriting" @click="startAutoWrite">开始生成</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showAutoWriteProgressDialog"
      title="AI编写进度"
      width="780px"
      :close-on-click-modal="false"
    >
      <div class="ai-progress-wrap">
        <div class="ai-progress-head">
          <div>任务ID：#{{ autoWriteTaskId || '-' }}</div>
          <el-tag :type="autoWriteTaskStatusTag">{{ autoWriteTaskStatusLabel }}</el-tag>
        </div>
        <el-progress :percentage="autoWriteProgressPercent" :stroke-width="16" :status="autoWriteProgressBarStatus" />
        <div class="ai-progress-summary">
          <span>总章节：{{ autoWriteProgress.total }}</span>
          <span>成功：{{ autoWriteProgress.success }}</span>
          <span>失败：{{ autoWriteProgress.failed }}</span>
        </div>
        <el-table :data="autoWriteProgress.steps" size="small" height="340">
          <el-table-column prop="sectionId" label="章节ID" width="90" />
          <el-table-column prop="title" label="章节标题" min-width="240" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" size="small">
                {{ row.status || 'RUNNING' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="error" label="错误信息" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showAutoWriteProgressDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CirclePlus, Delete, Operation } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig } from '@wangeditor/editor'
import { SlateEditor, SlateTransforms, DomEditor } from '@wangeditor/editor'
import { api } from '@/api'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

type ImageMeta = {
  index: number
  path: number[]
  src: string
  width: number
  align: 'left' | 'center' | 'right'
  caption: string
}
type CitationRow = {
  paragraphIndex: number
  chunkIds: string[]
  context: string
}

const route = useRoute()
const router = useRouter()
const document = ref<any>(null)
const sectionTree = ref<any[]>([])
const currentSection = ref<any>(null)
const content = ref('')
const editorRef = shallowRef<IDomEditor>()
const editorShellRef = ref<HTMLElement | null>(null)

const showCreateSection = ref(false)
const showAssetDialog = ref(false)
const showApplyTemplate = ref(false)
const showSaveTemplate = ref(false)
const showAutoWriteDialog = ref(false)
const showAutoWriteProgressDialog = ref(false)
const autoWriting = ref(false)
const autoWriteKnowledgeBases = ref<any[]>([])
const autoWriteTaskTimer = ref<number | null>(null)
const autoWriteTaskId = ref<number | null>(null)
const autoWriteTaskStatus = ref<string>('PENDING')
const autoWriteProgress = reactive({
  total: 0,
  success: 0,
  failed: 0,
  steps: [] as Array<{ sectionId?: number; title?: string; status?: string; error?: string }>
})
const templates = ref<any[]>([])
const rightTab = ref<'image' | 'citation'>('image')
const images = ref<ImageMeta[]>([])
const citationRows = ref<CitationRow[]>([])
const selectedImageIndex = ref(-1)
const imageForm = reactive({
  src: '',
  width: 320,
  align: 'left' as 'left' | 'center' | 'right',
  caption: ''
})

const saving = ref(false)
const dirty = ref(false)
const isProgrammaticChange = ref(false)
const lastSavedContent = ref('')
const lastSavedAt = ref<number | null>(null)
let imageSyncTimer: number | null = null
let applyTimer: number | null = null
const syncingImageForm = ref(false)

const textLength = computed(() => {
  if (!editorRef.value) return 0
  return (editorRef.value.getText() || '').trim().length
})
const selectedImage = computed(() => images.value.find(item => item.index === selectedImageIndex.value) || null)
const saveStatusText = computed(() => {
  if (saving.value) return '保存中...'
  if (dirty.value) return '有未保存修改'
  if (lastSavedAt.value) return `最近保存：${new Date(lastSavedAt.value).toLocaleTimeString()}`
  return '尚未保存'
})
const imageMarkerPreview = computed(() => {
  if (!selectedImage.value) return ''
  return `[img width=${imageForm.width} align=${imageForm.align} caption="${imageForm.caption || ''}"]${imageForm.src}`
})
const autoWriteTaskStatusLabel = computed(() => {
  if (autoWriteTaskStatus.value === 'RUNNING') return '执行中'
  if (autoWriteTaskStatus.value === 'SUCCESS') return '已完成'
  if (autoWriteTaskStatus.value === 'FAILED') return '失败'
  return autoWriteTaskStatus.value || 'PENDING'
})
const autoWriteTaskStatusTag = computed(() => {
  if (autoWriteTaskStatus.value === 'SUCCESS') return 'success'
  if (autoWriteTaskStatus.value === 'FAILED') return 'danger'
  return 'info'
})
const autoWriteProgressPercent = computed(() => {
  const total = autoWriteProgress.total || 0
  if (total <= 0) return autoWriteTaskStatus.value === 'RUNNING' ? 5 : 0
  const done = (autoWriteProgress.success || 0) + (autoWriteProgress.failed || 0)
  return Math.max(0, Math.min(100, Math.round((done / total) * 100)))
})
const autoWriteProgressBarStatus = computed(() => {
  if (autoWriteTaskStatus.value === 'FAILED') return 'exception'
  if (autoWriteTaskStatus.value === 'SUCCESS') return 'success'
  return undefined
})

const toolbarConfig = {}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: '在此编辑章节内容，支持图片上传、表格、标题、列表...',
  MENU_CONF: {
    uploadImage: {
      maxFileSize: 10 * 1024 * 1024,
      allowedFileTypes: ['image/*'],
      customUpload: async (file: File, insertFn: (url: string, alt?: string, href?: string) => void) => {
        const formData = new FormData()
        formData.append('file', file)
        const { data } = await api.uploadEditorImage(formData)
        // 仅插入图片本身，不附带超链接包装，避免后续样式和选中行为异常
        insertFn(data.url, data.originalName || file.name)
        setTimeout(syncImageList, 80)
      }
    }
  }
}

const sectionForm = reactive({
  title: '',
  level: 1,
  sortIndex: 1,
  parentId: null as number | null
})
const assetForm = reactive({
  versionId: '',
  industryTag: '',
  scopeTag: '',
  isWinning: false,
  keywords: ''
})
const templateApplyForm = reactive({ templateId: undefined as number | undefined })
const templateSaveForm = reactive({ name: '', description: '' })
const autoWriteForm = reactive({
  knowledgeBaseId: undefined as number | undefined,
  projectParams: '',
  overwriteExisting: true
})
const centerRenameEditing = ref(false)
const centerRenameTitle = ref('')
const centerRenameInputRef = ref()

const normalizeToHtml = (raw: string) => {
  if (!raw) return ''
  let trimmed = raw.trim()
  trimmed = trimmed.replace(/\[img(?:\s+([^\]]+))?](\S+)/gi, (_m, attrsRaw = '', url = '') => {
    const attrs = String(attrsRaw || '')
    const captionMatch = attrs.match(/caption=(?:"([^"]*)"|'([^']*)'|(\S+))/i)
    const alt = (captionMatch?.[1] || captionMatch?.[2] || captionMatch?.[3] || '图片').replace(/"/g, '')
    const src = String(url || '').startsWith('/') ? String(url || '') : `/${String(url || '')}`
    return `<img src="${src}" alt="${alt}" />`
  })
  trimmed = trimmed.replace(/(<img[^>]*\ssrc=["'])(files\/[^"']*)(["'][^>]*>)/gi, '$1/$2$3')
  trimmed = trimmed.replace(/(<img[^>]*\ssrc=["'])(knowledge-images\/[^"']*)(["'][^>]*>)/gi, '$1/files/$2$3')
  if (/<[a-z][\s\S]*>/i.test(trimmed)) {
    if (trimmed.includes('&lt;table') || trimmed.includes('&lt;tr') || trimmed.includes('&lt;td')) {
      const textarea = window.document.createElement('textarea')
      textarea.innerHTML = trimmed
      return textarea.value
    }
    return trimmed
  }
  const markdownHtml = marked.parse(trimmed, { gfm: true, breaks: true, async: false }) as string
  const cleaned = DOMPurify.sanitize(markdownHtml)
  if (cleaned && cleaned.trim()) {
    return cleaned
  }
  const escaped = trimmed.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.split('\n').map(line => `<p>${line || '<br>'}</p>`).join('')
}

const loadCitationRows = async (sectionId?: number) => {
  if (!sectionId) {
    citationRows.value = []
    return
  }
  try {
    const { data } = await api.listSectionChunkRefs(sectionId)
    const grouped = new Map<number, CitationRow>()
    for (const item of (data || [])) {
      const paragraphIndex = Number(item.paragraphIndex || 0)
      if (!paragraphIndex) continue
      const chunkId = String(item.chunkId)
      const row = grouped.get(paragraphIndex)
      if (!row) {
        grouped.set(paragraphIndex, {
          paragraphIndex,
          chunkIds: [chunkId],
          context: item.quoteText || ''
        })
      } else {
        if (!row.chunkIds.includes(chunkId)) row.chunkIds.push(chunkId)
        if (!row.context && item.quoteText) row.context = item.quoteText
      }
    }
    citationRows.value = Array.from(grouped.values()).sort((a, b) => a.paragraphIndex - b.paragraphIndex)
  } catch {
    citationRows.value = []
  }
}
// ─── 图片管理：基于 Slate 模型的统一读写，解决 DOM 与数据不同步问题 ───

/** 从 Slate 编辑器模型读取所有 image 节点，构建图片列表（替代旧的 DOMParser 方案） */
const syncImageList = () => {
  const editor = editorRef.value
  if (!editor) {
    images.value = []
    selectedImageIndex.value = -1
    imageForm.src = ''
    return
  }
  try {
    const entries = Array.from(
      SlateEditor.nodes(editor as any, {
        at: [],
        match: (n: any) => !!(n && n.type === 'image'),
      })
    )
    const result: ImageMeta[] = entries.map(([node, path], idx) => {
      const n = node as any
      const widthStr: string = n.style?.width || ''
      const width = parseInt(widthStr) || 320
      // 对齐信息从 DOM wrapper 读取（WangEditor 图片 Slate 节点不含 align 属性）
      let align: 'left' | 'center' | 'right' = 'left'
      try {
        const dom = DomEditor.toDOMNode(editor, n) as HTMLElement
        const wrapper = dom.closest?.('[data-w-e-type="image"]') as HTMLElement | null
        const da = dom.getAttribute?.('data-align') || wrapper?.getAttribute('data-align') || ''
        if (da === 'center') align = 'center'
        else if (da === 'right') align = 'right'
        else if (wrapper) {
          const jc = wrapper.style.justifyContent || ''
          if (jc === 'center') align = 'center'
          else if (jc.includes('end')) align = 'right'
        }
      } catch { /* 节点可能尚未挂载到 DOM */ }
      return { index: idx, path: [...path], src: n.src || '', width, align, caption: n.alt || '' }
    })
    images.value = result
    if (!result.length) {
      selectedImageIndex.value = -1
      imageForm.src = ''
      return
    }
    if (selectedImageIndex.value < 0 || !result.find(i => i.index === selectedImageIndex.value)) {
      selectedImageIndex.value = result[0].index
    }
    fillImageForm(selectedImageIndex.value)
  } catch {
    images.value = []
  }
}

/** 将选中图片的属性填充到右侧编辑表单 */
const fillImageForm = (index: number) => {
  const meta = images.value.find(i => i.index === index)
  if (!meta) return
  syncingImageForm.value = true
  imageForm.src = meta.src
  imageForm.width = meta.width
  imageForm.align = meta.align
  imageForm.caption = meta.caption
  nextTick(() => { syncingImageForm.value = false })
}

/** 在右侧面板中选择一张图片 */
const selectImageByIndex = (index: number) => {
  selectedImageIndex.value = index
  fillImageForm(index)
  rightTab.value = 'image'
}

/**
 * 通过 Slate API 修改图片属性（宽度、说明），通过 DOM 设置对齐方式。
 * 这是唯一的写入入口，保证数据一致性。
 */
const applyImageProps = () => {
  const editor = editorRef.value
  const meta = selectedImage.value
  if (!editor || !meta) return
  const width = Math.max(80, Math.min(1200, Number(imageForm.width) || 320))
  // 1. 通过 Slate API 修改 width 和 alt —— 自动同步到 HTML 和 DOM
  try {
    SlateTransforms.setNodes(
      editor as any,
      { style: { width: `${width}px` }, alt: imageForm.caption?.trim() || '' } as any,
      { at: meta.path }
    )
  } catch {
    // path 可能因内容变动而过期，刷新后重试
    syncImageList()
    return
  }
  // 2. 对齐方式通过 DOM 操作（WangEditor 图片节点不支持原生 align 属性）
  try {
    const entries = Array.from(
      SlateEditor.nodes(editor as any, { at: [], match: (n: any) => n.type === 'image' })
    )
    const entry = entries[meta.index]
    if (entry) {
      const dom = DomEditor.toDOMNode(editor, entry[0]) as HTMLElement
      const wrapper = dom.closest('[data-w-e-type="image"]') as HTMLElement | null
      dom.setAttribute('data-align', imageForm.align)
      if (wrapper) {
        wrapper.setAttribute('data-align', imageForm.align)
        wrapper.style.display = 'flex'
        wrapper.style.width = '100%'
        wrapper.style.justifyContent =
          imageForm.align === 'center' ? 'center'
          : imageForm.align === 'right' ? 'flex-end'
          : 'flex-start'
      }
    }
  } catch { /* 忽略 DOM 操作异常 */ }
  dirty.value = true
  nextTick(syncImageList)
}

/** 防抖应用图片属性变更 */
const applyImagePropsSilently = (debounceMs = 0) => {
  if (syncingImageForm.value || selectedImageIndex.value < 0) return
  if (applyTimer) { window.clearTimeout(applyTimer); applyTimer = null }
  if (debounceMs > 0) {
    applyTimer = window.setTimeout(applyImageProps, debounceMs)
  } else {
    applyImageProps()
  }
}

const setProgrammaticContent = (html: string) => {
  isProgrammaticChange.value = true
  content.value = html
  lastSavedContent.value = html
  dirty.value = false
  setTimeout(() => {
    isProgrammaticChange.value = false
    syncImageList()
  }, 60)
}

const handleEditorCreated = (editor: IDomEditor) => {
  editorRef.value = editor
  // 编辑器初始化完成后从 Slate 模型同步图片列表
  setTimeout(syncImageList, 80)
}

/** 点击编辑器中的图片时，通过 DomEditor 精确定位 Slate 节点并选中 */
const handleEditorShellClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target || target.tagName.toLowerCase() !== 'img') return
  const editor = editorRef.value
  if (!editor) return
  try {
    const slateNode = DomEditor.toSlateNode(editor, target)
    if (!slateNode || (slateNode as any).type !== 'image') return
    const path = DomEditor.findPath(editor, slateNode)
    const matchIdx = images.value.findIndex(
      img => img.path.length === path.length && img.path.every((v, i) => v === path[i])
    )
    if (matchIdx >= 0) selectImageByIndex(matchIdx)
  } catch { /* 忽略罕见的 DOM 不一致 */ }
}

const persistCurrentSection = async (summary: string, showMessage: boolean) => {
  if (!currentSection.value || saving.value) return
  if (!dirty.value) {
    if (showMessage) {
      ElMessage.info('当前无变更，无需保存')
    }
    return
  }
  try {
    saving.value = true
    // 章节层仅保存当前内容（覆盖式），避免高频操作导致版本爆炸
    const { data } = await api.createVersion(currentSection.value.id, { content: content.value, summary })
    currentSection.value.currentVersionId = data.id
    lastSavedContent.value = content.value
    dirty.value = false
    lastSavedAt.value = Date.now()
    await loadCitationRows(currentSection.value.id)
    if (showMessage) ElMessage.success('已保存')
  } catch (err: any) {
    if (showMessage) {
      ElMessage.error(err?.response?.data?.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}
const confirmLeaveIfDirty = async () => {
  if (!dirty.value || saving.value) return true
  try {
    await ElMessageBox.confirm('当前章节有未保存修改，确认离开吗？', '提示', {
      type: 'warning',
      confirmButtonText: '离开',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

const load = async () => {
  const docId = Number(route.params.id)
  const docRes = await api.getDocument(docId)
  document.value = docRes.data
  const treeRes = await api.listSectionTree(docId)
  sectionTree.value = treeRes.data
  const tplRes = await api.listSectionTemplates()
  templates.value = tplRes.data
}
const handleSelect = async (node: any) => {
  if (!(await confirmLeaveIfDirty())) return
  currentSection.value = node
  if (node.currentVersionId) {
    const v = await api.getVersion(node.id, node.currentVersionId)
    setProgrammaticContent(normalizeToHtml(v.data.content || ''))
    await loadCitationRows(node.id)
    if (v.data.createdAt) {
      lastSavedAt.value = new Date(v.data.createdAt).getTime()
    }
  } else {
    setProgrammaticContent('')
    lastSavedAt.value = null
    citationRows.value = []
  }
}
const findNodeAndSiblings = (nodeId: number, nodes: any[] = sectionTree.value, parent: any = null): { node: any; siblings: any[]; parent: any | null } | null => {
  for (const item of nodes) {
    if (item.id === nodeId) {
      return { node: item, siblings: nodes, parent }
    }
    if (item.children?.length) {
      const found = findNodeAndSiblings(nodeId, item.children, item)
      if (found) return found
    }
  }
  return null
}
const startCenterRename = async () => {
  if (!currentSection.value) return
  centerRenameEditing.value = true
  centerRenameTitle.value = currentSection.value.title || ''
  await nextTick()
  centerRenameInputRef.value?.focus?.()
}
const cancelCenterRename = () => {
  centerRenameEditing.value = false
  centerRenameTitle.value = ''
}
const commitCenterRename = async () => {
  if (!centerRenameEditing.value || !currentSection.value) return
  const title = centerRenameTitle.value.trim()
  centerRenameEditing.value = false
  if (!title || title === currentSection.value.title) return
  try {
    await api.updateSection(currentSection.value.id, { title })
    const found = findNodeAndSiblings(currentSection.value.id)
    if (found) {
      found.node.title = title
    }
    currentSection.value.title = title
    ElMessage.success('章节名称已修改')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '修改失败')
  }
}
const createSectionAt = async (anchor: any, mode: 'child' | 'sibling') => {
  const result: any = await ElMessageBox.prompt('请输入章节名称', mode === 'child' ? '新增子章节' : '新增同级章节', {
    inputValue: mode === 'child' ? `${anchor.title}-子章节` : `${anchor.title}-同级`,
    confirmButtonText: '创建',
    cancelButtonText: '取消'
  }).catch(() => null)
  if (!result?.value?.trim()) return
  const name = result.value.trim()
  const found = findNodeAndSiblings(anchor.id)
  if (!found) return
  const targetSiblings = mode === 'child' ? (found.node.children || []) : found.siblings
  const maxSort = targetSiblings.reduce((m: number, n: any) => Math.max(m, Number(n.sortIndex || 0)), 0)
  const payload = {
    title: name,
    parentId: mode === 'child' ? anchor.id : (anchor.parentId || null),
    level: mode === 'child' ? Number(anchor.level || 1) + 1 : Number(anchor.level || 1),
    sortIndex: maxSort + 1
  }
  try {
    const { data } = await api.createSection(Number(route.params.id), payload)
    ElMessage.success('章节创建成功')
    await load()
    const created = findNodeAndSiblings(data.id)
    if (created) {
      await handleSelect(created.node)
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '创建失败')
  }
}
const createChildSection = async (node: any) => {
  if (!(await confirmLeaveIfDirty())) return
  await createSectionAt(node, 'child')
}
const createSiblingSection = async (node: any) => {
  if (!(await confirmLeaveIfDirty())) return
  await createSectionAt(node, 'sibling')
}
const deleteSectionNode = async (node: any) => {
  const currentId = currentSection.value?.id
  if (currentId === node.id && !(await confirmLeaveIfDirty())) return
  try {
    await ElMessageBox.confirm(`确认删除章节「${node.title}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.deleteSection(node.id)
    if (currentSection.value?.id === node.id) {
      currentSection.value = null
      content.value = ''
      dirty.value = false
      lastSavedContent.value = ''
      citationRows.value = []
    }
    ElMessage.success('章节已删除')
    await load()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}
const createSection = async () => {
  try {
    const docId = Number(route.params.id)
    await api.createSection(docId, sectionForm)
    ElMessage.success('章节创建成功')
    showCreateSection.value = false
    await load()
  } catch {
    ElMessage.error('章节创建失败')
  }
}
const saveCurrentSection = async () => {
  await persistCurrentSection('手动保存', true)
}
const lockSection = async () => {
  if (!currentSection.value) return
  await api.lockSection(currentSection.value.id)
  ElMessage.success('已锁定')
}
const unlockSection = async () => {
  if (!currentSection.value) return
  await api.unlockSection(currentSection.value.id)
  ElMessage.success('已解锁')
}
const openAssetDialog = () => {
  if (!currentSection.value) return
  assetForm.versionId = currentSection.value.currentVersionId || ''
  showAssetDialog.value = true
}
const createAsset = async () => {
  try {
    await api.createAsset(currentSection.value.id, assetForm)
    ElMessage.success('资产已沉淀')
    showAssetDialog.value = false
  } catch {
    ElMessage.error('沉淀失败')
  }
}
const aiRewrite = async () => {
  if (!currentSection.value) return
  if (dirty.value) {
    ElMessage.warning('请先保存当前章节，再执行 AI 改写')
    return
  }
  if (!currentSection.value.currentVersionId) {
    ElMessage.warning('请先保存当前章节，再执行 AI 改写')
    return
  }
  try {
    const res = await api.aiRewrite({
      sectionId: currentSection.value.id,
      sourceVersionId: currentSection.value.currentVersionId,
      projectParams: JSON.stringify({ projectId: document.value.projectId })
    })
    ElMessage.success(`AI任务已创建: ${res.data.id}`)
    await handleSelect(currentSection.value)
  } catch {
    ElMessage.error('AI改写失败')
  }
}
const openAutoWriteDialog = async () => {
  showAutoWriteDialog.value = true
  if (!autoWriteKnowledgeBases.value.length) {
    const { data } = await api.listKnowledgeBases()
    autoWriteKnowledgeBases.value = data || []
  }
  autoWriteForm.projectParams = JSON.stringify({ projectId: document.value?.projectId, documentId: document.value?.id })
}
const clearAutoWriteTimer = () => {
  if (autoWriteTaskTimer.value !== null) {
    window.clearInterval(autoWriteTaskTimer.value)
    autoWriteTaskTimer.value = null
  }
}
const resetAutoWriteProgress = () => {
  autoWriteTaskId.value = null
  autoWriteTaskStatus.value = 'PENDING'
  autoWriteProgress.total = 0
  autoWriteProgress.success = 0
  autoWriteProgress.failed = 0
  autoWriteProgress.steps = []
}
const applyAutoWriteProgress = (task: any) => {
  autoWriteTaskStatus.value = task?.status || 'PENDING'
  const raw = task?.response
  if (!raw) return
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    autoWriteProgress.total = Number(parsed?.total || 0)
    autoWriteProgress.success = Number(parsed?.success || 0)
    autoWriteProgress.failed = Number(parsed?.failed || 0)
    autoWriteProgress.steps = Array.isArray(parsed?.steps) ? parsed.steps : []
  } catch {
    // ignore invalid payload
  }
}
const startAutoWrite = async () => {
  if (!document.value?.id) return
  if (dirty.value) {
    ElMessage.warning('请先保存当前章节，再执行全文自动编写')
    return
  }
  try {
    autoWriting.value = true
    resetAutoWriteProgress()
    const { data } = await api.aiAutoWriteDocument({
      documentId: document.value.id,
      knowledgeBaseId: autoWriteForm.knowledgeBaseId,
      projectParams: autoWriteForm.projectParams,
      overwriteExisting: autoWriteForm.overwriteExisting
    })
    const taskId = data?.id
    if (!taskId) {
      ElMessage.error('任务创建失败')
      return
    }
    autoWriteTaskId.value = Number(taskId)
    ElMessage.success(`已启动自动编写任务 #${taskId}`)
    showAutoWriteDialog.value = false
    showAutoWriteProgressDialog.value = true
    clearAutoWriteTimer()
    autoWriteTaskTimer.value = window.setInterval(async () => {
      try {
        const taskRes = await api.getAiTask(taskId)
        const status = taskRes.data?.status
        applyAutoWriteProgress(taskRes.data)
        if (status === 'RUNNING') return
        clearAutoWriteTimer()
        autoWriting.value = false
        if (status === 'SUCCESS') {
          ElMessage.success('AI 自动编写完成')
        } else {
          ElMessage.error(taskRes.data?.errorMessage || 'AI 自动编写失败')
        }
        await load()
      } catch (e) {
        clearAutoWriteTimer()
        autoWriting.value = false
        autoWriteTaskStatus.value = 'FAILED'
      }
    }, 2500)
  } catch {
    autoWriting.value = false
    autoWriteTaskStatus.value = 'FAILED'
    ElMessage.error('启动自动编写失败')
  }
}
const submitReview = async () => {
  if (!currentSection.value) return
  if (dirty.value) {
    ElMessage.warning('请先保存当前章节，再提交审核')
    return
  }
  if (!currentSection.value.currentVersionId) return
  try {
    await api.submitReview(currentSection.value.id, {
      versionId: currentSection.value.currentVersionId,
      comment: '提交审核'
    })
    ElMessage.success('已提交审核')
  } catch {
    ElMessage.error('提交失败')
  }
}
const refresh = async () => {
  if (!(await confirmLeaveIfDirty())) return
  await load()
}
const applyTemplate = async () => {
  if (!templateApplyForm.templateId || !document.value) return
  await api.applySectionTemplate(document.value.id, templateApplyForm.templateId, { clearExisting: false })
  ElMessage.success('模板已套用')
  showApplyTemplate.value = false
  await load()
}
const saveAsTemplate = async () => {
  if (!document.value || !templateSaveForm.name.trim()) return
  await api.createSectionTemplateFromDocument({
    documentId: document.value.id,
    name: templateSaveForm.name,
    description: templateSaveForm.description
  })
  ElMessage.success('章节模板已保存')
  showSaveTemplate.value = false
  templateSaveForm.name = ''
  templateSaveForm.description = ''
  const tplRes = await api.listSectionTemplates()
  templates.value = tplRes.data
}
const back = async () => {
  if (!(await confirmLeaveIfDirty())) return
  if (document.value) router.push(`/projects/${document.value.projectId}`)
}
const onBeforeUnload = (event: BeforeUnloadEvent) => {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

const handleEditorShortcut = async (event: KeyboardEvent) => {
  const isSave = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's'
  if (!isSave) return
  event.preventDefault()
  await saveCurrentSection()
}

watch(content, () => {
  if (isProgrammaticChange.value) return
  dirty.value = content.value !== lastSavedContent.value
  if (imageSyncTimer) {
    clearTimeout(imageSyncTimer)
  }
  imageSyncTimer = window.setTimeout(syncImageList, 220)
})
watch(() => imageForm.align, () => {
  applyImagePropsSilently(0)
})
watch(() => imageForm.width, () => {
  applyImagePropsSilently(120)
})
watch(() => imageForm.caption, () => {
  applyImagePropsSilently(200)
})

onBeforeRouteLeave(async (_to, _from, next) => {
  if (await confirmLeaveIfDirty()) next()
  else next(false)
})

onMounted(async () => {
  await load()
  window.addEventListener('beforeunload', onBeforeUnload)
  window.addEventListener('keydown', handleEditorShortcut)
})

onBeforeUnmount(() => {
  if (imageSyncTimer) window.clearTimeout(imageSyncTimer)
  if (applyTimer) window.clearTimeout(applyTimer)
  clearAutoWriteTimer()
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener('keydown', handleEditorShortcut)
  editorRef.value?.destroy()
})
</script>

<style scoped>
.editor {
  height: calc(100vh - 96px);
  overflow: hidden;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

.editor-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1.25fr) minmax(320px, 0.85fr);
  gap: 16px;
  margin-top: 12px;
  height: calc(100vh - 196px);
  min-height: 680px;
}

.editor :deep(.card) {
  border: 1px solid #dfe6f1;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #fbfdff);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
  padding: 14px;
}

.header {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(6px);
}

.tree-header,
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tree-tools {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 8px;
}

.tree-node-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.tree-node-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

:deep(.el-tree-node__content:hover .tree-node-actions) {
  opacity: 1;
}

.center-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.center-title {
  cursor: pointer;
}

.center-title-input {
  width: 320px;
}

.tree,
.content,
.versions {
  min-width: 0;
}

.tree {
  max-height: 100%;
  overflow: auto;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  max-height: 100%;
}

.content-empty {
  justify-content: center;
  align-items: center;
}

.editor-shell {
  border: 1px solid #dce4ef;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.footer-actions-top {
  padding: 10px 12px;
  border-bottom: 1px solid #e5ebf5;
  justify-content: flex-end;
}

.editor-toolbar {
  border-bottom: 1px solid #e5ebf5;
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fff;
}

.editor-main {
  min-height: 420px;
  max-height: calc(100vh - 470px);
  overflow-y: auto;
  background: #f1f4f8;
}

/* A4 编辑纸张：210mm * 297mm，按 96dpi 约 794px * 1123px */
.editor-main :deep(.w-e-text-container) {
  background: transparent;
}

.editor-main :deep(.w-e-text-container [data-slate-editor]) {
  width: 794px;
  min-height: 1123px;
  margin: 18px auto;
  padding: 64px 72px;
  background: #fff;
  border: 1px solid #d9e1ec;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
  box-sizing: border-box;
  line-height: 1.6;
}

.editor-main :deep(.w-e-text-container p) {
  margin: 0 0 10px;
}

.editor-hint {
  font-size: 12px;
  color: #6b7a90;
}

.versions {
  max-height: 100%;
  overflow: auto;
}

.versions :deep(.el-tabs__header) {
  position: sticky;
  top: 0;
  z-index: 6;
  background: #fff;
}

.image-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.image-list-title {
  color: #475569;
  font-size: 13px;
}

.image-list {
  max-height: 170px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-item {
  display: flex;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px;
  cursor: pointer;
}

.image-item.active {
  border-color: #1a73e8;
  background: #eef4ff;
}

.image-item img {
  width: 58px;
  height: 44px;
  object-fit: cover;
  border-radius: 4px;
}

.image-item-meta {
  min-width: 0;
  flex: 1;
}

.one-line {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
}

.subtle {
  color: #64748b;
}

.image-form {
  margin-top: 6px;
  border-top: 1px dashed #dbe3ef;
  padding-top: 10px;
}

.citation-card-list {
  max-height: 520px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.citation-card {
  border: 1px solid #dbe5f2;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fbfdff;
}

.citation-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.citation-index {
  font-size: 12px;
  color: #6b7a90;
}

.citation-content {
  font-size: 13px;
  line-height: 1.6;
  color: #24364f;
  white-space: pre-wrap;
  word-break: break-word;
}

.header .header-title,
.content-header .header-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.footer-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.status {
  font-size: 12px;
  color: #60708a;
}

.ai-progress-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #2b3e57;
  font-size: 13px;
}

.ai-progress-summary {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #4a5f7a;
}

:deep(.el-tree) {
  overflow: auto;
}

:deep(.el-tree-node__content) {
  border-radius: 8px;
  margin: 2px 0;
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e8f0fe;
}

:deep(.el-table) {
  width: 100%;
}

:deep(.w-e-text-container img) {
  max-width: 100%;
  border-radius: 4px;
  outline: 1px solid transparent;
  display: block;
  cursor: pointer;
}

:deep(.w-e-text-container [data-w-e-type="image"]) {
  display: flex;
  width: 100%;
  justify-content: flex-start;
  line-height: 0;
  padding: 0;
  margin: 8px 0;
  box-sizing: border-box;
}

:deep(.w-e-text-container [data-w-e-type="image"][data-align="center"]) {
  justify-content: center;
}

:deep(.w-e-text-container [data-w-e-type="image"][data-align="right"]) {
  justify-content: flex-end;
}

:deep(.w-e-text-container img:hover) {
  outline: 2px dashed #a7b6cc;
}

@media (max-width: 1380px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .tree,
  .versions,
  .content {
    max-height: none;
    min-height: auto;
  }

  .editor-main {
    max-height: 640px;
  }
}

@media (max-width: 980px) {
  .editor-main :deep(.w-e-text-container [data-slate-editor]) {
    width: calc(100% - 20px);
    min-height: 900px;
    padding: 28px 20px;
  }
}
</style>
