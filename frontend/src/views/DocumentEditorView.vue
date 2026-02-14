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
        />
      </div>

      <div class="card content" v-if="currentSection">
        <div class="content-header">
          <div>
            <div class="header-title">{{ currentSection.title }}</div>
            <div class="status">
              状态：{{ currentSection.status }} | 字数：{{ textLength }} | {{ autosaveText }}
            </div>
          </div>
          <div class="actions">
            <el-button size="small" @click="lockSection">锁定</el-button>
            <el-button size="small" @click="unlockSection">解锁</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveVersion">保存版本</el-button>
          </div>
        </div>

        <div ref="editorShellRef" class="editor-shell" @click="handleEditorShellClick" @mousedown="handleEditorShellMouseDown">
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
          支持：标题、表格、列表、图片上传/粘贴；图片可在编辑区右边缘拖拽缩放，并在右侧属性面板设置对齐与说明。
        </div>

        <div class="footer-actions">
          <el-button @click="openAssetDialog">沉淀为资产</el-button>
          <el-button type="success" @click="aiRewrite">AI改写</el-button>
          <el-button type="warning" @click="submitReview">提交审核</el-button>
        </div>
      </div>

      <div class="card versions" v-if="currentSection">
        <el-tabs v-model="rightTab">
          <el-tab-pane label="版本历史" name="versions">
            <el-table :data="versions" height="480">
              <el-table-column prop="id" label="版本ID" width="90" />
              <el-table-column prop="sourceType" label="来源" width="100" />
              <el-table-column prop="summary" label="摘要" />
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button size="small" @click="loadVersion(scope.row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
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
                <el-form-item label="拖拽">
                  <el-tag type="info">编辑区内在图片右边缘按住鼠标拖拽即可缩放</el-tag>
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
                <el-form-item>
                  <el-button type="primary" @click="applyImageProps">应用属性</el-button>
                </el-form-item>
                <el-form-item label="导出标记">
                  <el-input :value="imageMarkerPreview" readonly />
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig } from '@wangeditor/editor'
import { api } from '@/api'

type ImageMeta = {
  index: number
  src: string
  width: number
  align: 'left' | 'center' | 'right'
  caption: string
}

const AUTO_SAVE_MS = 30000

const route = useRoute()
const router = useRouter()
const document = ref<any>(null)
const sectionTree = ref<any[]>([])
const currentSection = ref<any>(null)
const versions = ref<any[]>([])
const content = ref('')
const editorRef = shallowRef<IDomEditor>()
const editorShellRef = ref<HTMLElement | null>(null)

const showCreateSection = ref(false)
const showAssetDialog = ref(false)
const showApplyTemplate = ref(false)
const showSaveTemplate = ref(false)
const templates = ref<any[]>([])
const rightTab = ref<'versions' | 'image'>('versions')
const images = ref<ImageMeta[]>([])
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
const lastAutoSavedAt = ref<number | null>(null)
let autoSaveTimer: number | null = null
let imageSyncTimer: number | null = null

const resizeState = reactive({
  active: false,
  startX: 0,
  startWidth: 0,
  target: null as HTMLImageElement | null
})

const textLength = computed(() => {
  if (!editorRef.value) return 0
  return (editorRef.value.getText() || '').trim().length
})
const selectedImage = computed(() => images.value.find(item => item.index === selectedImageIndex.value) || null)
const autosaveText = computed(() => {
  if (saving.value) return '自动保存中...'
  if (dirty.value) return '有未保存修改'
  if (lastAutoSavedAt.value) return `已自动保存 ${new Date(lastAutoSavedAt.value).toLocaleTimeString()}`
  return '内容已保存'
})
const imageMarkerPreview = computed(() => {
  if (!selectedImage.value) return ''
  return `[img width=${imageForm.width} align=${imageForm.align} caption="${imageForm.caption || ''}"]${imageForm.src}`
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
        insertFn(data.url, data.originalName || file.name, data.url)
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

const normalizeToHtml = (raw: string) => {
  if (!raw) return ''
  const trimmed = raw.trim()
  if (/<[a-z][\s\S]*>/i.test(trimmed)) return trimmed
  const escaped = trimmed.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.split('\n').map(line => `<p>${line || '<br>'}</p>`).join('')
}
const parseWidth = (img: HTMLImageElement) => {
  const fromWidth = Number.parseInt(img.getAttribute('width') || '', 10)
  if (!Number.isNaN(fromWidth) && fromWidth > 0) return fromWidth
  const fromStyle = Number.parseInt((img.style.width || '').replace(/px$/, ''), 10)
  if (!Number.isNaN(fromStyle) && fromStyle > 0) return fromStyle
  return Math.round(img.getBoundingClientRect().width) || 320
}
const parseAlign = (img: HTMLImageElement): 'left' | 'center' | 'right' => {
  const dataAlign = (img.getAttribute('data-align') || '').toLowerCase()
  if (dataAlign === 'center' || dataAlign === 'right') return dataAlign
  const style = (img.getAttribute('style') || '').toLowerCase()
  if (style.includes('margin-left: auto') && style.includes('margin-right: auto')) return 'center'
  if (style.includes('margin-left: auto')) return 'right'
  return 'left'
}
const parseImagesFromHtml = (html: string): ImageMeta[] => {
  const doc = new DOMParser().parseFromString(html || '', 'text/html')
  return Array.from(doc.querySelectorAll('img')).map((node, idx) => {
    const img = node as HTMLImageElement
    const caption = img.getAttribute('data-caption') || img.getAttribute('alt') || ''
    return {
      index: idx,
      src: img.getAttribute('src') || '',
      width: parseWidth(img),
      align: parseAlign(img),
      caption
    }
  })
}
const setProgrammaticContent = (html: string) => {
  isProgrammaticChange.value = true
  content.value = html
  lastSavedContent.value = html
  dirty.value = false
  setTimeout(() => {
    isProgrammaticChange.value = false
    syncImageList()
  }, 0)
}
const syncImageList = () => {
  images.value = parseImagesFromHtml(content.value)
  if (!images.value.length) {
    selectedImageIndex.value = -1
    imageForm.src = ''
    return
  }
  if (selectedImageIndex.value < 0 || !images.value.find(item => item.index === selectedImageIndex.value)) {
    selectedImageIndex.value = images.value[0].index
  }
  fillImageForm(selectedImageIndex.value)
}
const fillImageForm = (index: number) => {
  const meta = images.value.find(item => item.index === index)
  if (!meta) return
  imageForm.src = meta.src
  imageForm.width = meta.width
  imageForm.align = meta.align
  imageForm.caption = meta.caption
}
const selectImageByIndex = (index: number) => {
  selectedImageIndex.value = index
  fillImageForm(index)
  rightTab.value = 'image'
}
const applyImageProps = () => {
  if (selectedImageIndex.value < 0) return
  const doc = new DOMParser().parseFromString(content.value || '', 'text/html')
  const list = Array.from(doc.querySelectorAll('img'))
  const target = list[selectedImageIndex.value] as HTMLImageElement | undefined
  if (!target) return
  const width = Math.max(80, Math.min(1200, Number(imageForm.width || 320)))
  target.setAttribute('width', String(width))
  target.style.width = `${width}px`
  target.style.height = 'auto'
  target.style.display = 'block'
  target.setAttribute('data-align', imageForm.align)
  if (imageForm.align === 'center') {
    target.style.margin = '0 auto'
  } else if (imageForm.align === 'right') {
    target.style.marginLeft = 'auto'
    target.style.marginRight = '0'
  } else {
    target.style.marginLeft = '0'
    target.style.marginRight = 'auto'
  }
  if (imageForm.caption?.trim()) {
    target.setAttribute('data-caption', imageForm.caption.trim())
    target.setAttribute('alt', imageForm.caption.trim())
  } else {
    target.removeAttribute('data-caption')
  }
  content.value = doc.body.innerHTML
  syncImageList()
  dirty.value = true
  ElMessage.success('图片属性已应用')
}
const handleEditorCreated = (editor: IDomEditor) => {
  editorRef.value = editor
}
const handleEditorShellClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target || target.tagName.toLowerCase() !== 'img') return
  const container = editorShellRef.value?.querySelector('.w-e-text-container')
  if (!container) return
  const all = Array.from(container.querySelectorAll('img'))
  const index = all.indexOf(target as HTMLImageElement)
  if (index >= 0) selectImageByIndex(index)
}
const handleEditorShellMouseDown = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target || target.tagName.toLowerCase() !== 'img') return
  const img = target as HTMLImageElement
  const rect = img.getBoundingClientRect()
  if (rect.right - event.clientX > 14) return
  resizeState.active = true
  resizeState.startX = event.clientX
  resizeState.startWidth = parseWidth(img)
  resizeState.target = img
  img.classList.add('img-resizing')
  event.preventDefault()
}
const onWindowMouseMove = (event: MouseEvent) => {
  if (!resizeState.active || !resizeState.target) return
  const next = Math.max(80, Math.min(1200, resizeState.startWidth + (event.clientX - resizeState.startX)))
  resizeState.target.style.width = `${next}px`
  resizeState.target.style.height = 'auto'
  imageForm.width = Math.round(next)
}
const onWindowMouseUp = () => {
  if (!resizeState.active) return
  resizeState.active = false
  if (resizeState.target) {
    resizeState.target.classList.remove('img-resizing')
  }
  resizeState.target = null
  const html = editorRef.value?.getHtml()
  if (html) {
    content.value = html
    dirty.value = true
    syncImageList()
  }
}

const persistVersion = async (summary: string, showMessage: boolean) => {
  if (!currentSection.value || saving.value || !dirty.value) return
  try {
    saving.value = true
    const { data } = await api.createVersion(currentSection.value.id, { content: content.value, summary })
    currentSection.value.currentVersionId = data.id
    versions.value = [data, ...versions.value.filter(v => v.id !== data.id)]
    lastSavedContent.value = content.value
    dirty.value = false
    if (summary === '自动保存') {
      lastAutoSavedAt.value = Date.now()
    }
    if (showMessage) ElMessage.success('版本已保存')
  } catch (err: any) {
    if (showMessage) {
      ElMessage.error(err?.response?.data?.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}
const autoSave = async () => {
  await persistVersion('自动保存', false)
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
  const res = await api.listVersions(node.id)
  versions.value = res.data
  if (node.currentVersionId) {
    const v = await api.getVersion(node.id, node.currentVersionId)
    setProgrammaticContent(normalizeToHtml(v.data.content || ''))
  } else {
    setProgrammaticContent('')
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
const saveVersion = async () => {
  await persistVersion('手动保存', true)
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
const loadVersion = async (row: any) => {
  if (!(await confirmLeaveIfDirty())) return
  const res = await api.getVersion(currentSection.value.id, row.id)
  setProgrammaticContent(normalizeToHtml(res.data.content || ''))
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
  await persistVersion('AI改写前自动保存', false)
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
const submitReview = async () => {
  if (!currentSection.value) return
  await persistVersion('提交审核前自动保存', false)
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

watch(content, () => {
  if (isProgrammaticChange.value) return
  dirty.value = content.value !== lastSavedContent.value
  if (imageSyncTimer) {
    clearTimeout(imageSyncTimer)
  }
  imageSyncTimer = window.setTimeout(syncImageList, 220)
})

onBeforeRouteLeave(async (_to, _from, next) => {
  if (await confirmLeaveIfDirty()) next()
  else next(false)
})

onMounted(async () => {
  await load()
  autoSaveTimer = window.setInterval(autoSave, AUTO_SAVE_MS)
  window.addEventListener('beforeunload', onBeforeUnload)
  window.addEventListener('mousemove', onWindowMouseMove)
  window.addEventListener('mouseup', onWindowMouseUp)
})

onBeforeUnmount(() => {
  if (autoSaveTimer) window.clearInterval(autoSaveTimer)
  if (imageSyncTimer) window.clearTimeout(imageSyncTimer)
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
  editorRef.value?.destroy()
})
</script>

<style scoped>
.editor {
  overflow-x: hidden;
}

.editor-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1.25fr) minmax(320px, 0.85fr);
  gap: 16px;
  margin-top: 16px;
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
}

.tree,
.content,
.versions {
  min-width: 0;
}

.tree {
  max-height: calc(100vh - 220px);
  overflow: auto;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-shell {
  border: 1px solid #dce4ef;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.editor-toolbar {
  border-bottom: 1px solid #e5ebf5;
}

.editor-main {
  min-height: 520px;
  max-height: 560px;
  overflow-y: auto;
}

.editor-hint {
  font-size: 12px;
  color: #6b7a90;
}

.versions {
  max-height: calc(100vh - 220px);
  overflow: auto;
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
  border-radius: 8px;
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
  opacity: 0.78;
}

:deep(.el-tree) {
  overflow: auto;
}

:deep(.el-table) {
  width: 100%;
}

:deep(.w-e-text-container img) {
  max-width: 100%;
  border-radius: 4px;
  outline: 1px solid transparent;
}

:deep(.w-e-text-container img:hover) {
  outline: 1px dashed #a7b6cc;
}

:deep(.w-e-text-container img.img-resizing) {
  outline: 2px solid #1a73e8;
}

@media (max-width: 1380px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .tree,
  .versions {
    max-height: none;
  }
}
</style>
