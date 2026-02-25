<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">知识库</div>
        <el-button type="primary" @click="showCreateKb = true">新建知识库</el-button>
      </div>
      <el-table :data="knowledgeBases" @row-click="selectKb">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" type="danger" @click.stop="removeKb(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card" style="margin-top: 16px" v-if="selectedKb">
      <div class="header">
        <div class="header-title">文档管理：{{ selectedKb.name }}</div>
      </div>
      <div class="actions-row">
        <el-upload :show-file-list="false" :auto-upload="false" :on-change="onFileChange" accept=".pdf,.docx,.ppt,.pptx,.txt,.md">
          <el-button>选择文件</el-button>
        </el-upload>
        <el-button type="primary" :disabled="!selectedFile" @click="uploadFile">上传并向量化</el-button>
        <el-button @click="showManual = true">手动添加内容</el-button>
        <el-button @click="openPackBinding">引入外部词典包</el-button>
        <el-button @click="reindexKb">重建索引</el-button>
      </div>
      <div class="upload-tip">上传文件会落盘，支持后续词典更新后重新抽取并重建向量。</div>

      <el-table :data="documents" style="margin-top: 12px">
        <el-table-column prop="id" label="文档ID" width="100" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="sourceType" label="来源" width="120" />
        <el-table-column prop="fileType" label="文件类型" width="120" />
        <el-table-column label="索引状态" width="220">
          <template #default="scope">
            <div style="display: flex; align-items: center; gap: 8px">
              <el-tag :type="indexStatusTagType(scope.row.indexStatus)" effect="light">
                {{ scope.row.indexStatus || 'SUCCESS' }}
              </el-tag>
              <span class="index-message" :title="scope.row.indexMessage || ''">{{ scope.row.indexMessage }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="索引进度" width="220">
          <template #default="scope">
            <el-progress
              :percentage="Number(scope.row.indexProgress || 0)"
              :status="scope.row.indexStatus === 'FAILED' ? 'exception' : (scope.row.indexStatus === 'SUCCESS' ? 'success' : undefined)"
              :stroke-width="14"
            />
          </template>
        </el-table-column>
        <el-table-column prop="storagePath" label="存储路径" />
        <el-table-column prop="visibility" label="可见性" width="140">
          <template #default="scope">
            <el-select :model-value="scope.row.visibility" size="small" style="width: 110px" @change="(v: string) => changeVisibility(scope.row, v)">
              <el-option label="PRIVATE" value="PRIVATE" />
              <el-option label="PUBLIC" value="PUBLIC" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button size="small" type="danger" @click="removeDocument(scope.row)">
              {{ scope.row.indexStatus === 'RUNNING' || scope.row.indexStatus === 'PENDING' ? '取消并删除' : '删除' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreateKb" title="新建知识库" width="420px">
      <el-form :model="kbForm">
        <el-form-item label="名称"><el-input v-model="kbForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="kbForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateKb = false">取消</el-button>
        <el-button type="primary" @click="createKb">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showManual" title="手动添加知识" width="520px">
      <el-form :model="manualForm">
        <el-form-item label="标题"><el-input v-model="manualForm.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="manualForm.content" type="textarea" :rows="8" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showManual = false">取消</el-button>
        <el-button type="primary" @click="addManual">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPackBinding" title="引入外部词典包" width="680px">
      <el-table :data="packBindings" border v-loading="packBindingLoading">
        <el-table-column prop="packName" label="词典包" min-width="200" />
        <el-table-column prop="packCode" label="编码" width="130" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="enabled" label="状态" width="90">
          <template #default="scope">{{ scope.row.enabled ? '启用' : '停用' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="toggleBinding(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button>
            <el-button link type="danger" @click="removeBinding(scope.row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 14px; display: flex; gap: 10px; align-items: center">
        <el-select v-model="packToAdd" placeholder="选择词典包" style="width: 300px">
          <el-option v-for="pack in allPacks" :key="pack.id" :label="pack.name" :value="pack.id" />
        </el-select>
        <el-input-number v-model="packPriority" :min="1" :max="999" />
        <el-button type="primary" @click="bindSelectedPack">引入</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const knowledgeBases = ref<any[]>([])
const selectedKb = ref<any>(null)
const documents = ref<any[]>([])
const selectedFile = ref<File | null>(null)
const showCreateKb = ref(false)
const showManual = ref(false)
const showPackBinding = ref(false)
const packBindings = ref<any[]>([])
const allPacks = ref<any[]>([])
const packToAdd = ref<number>()
const packPriority = ref(100)
const packBindingLoading = ref(false)
const pollingTimer = ref<number | null>(null)

const kbForm = reactive({ name: '', description: '' })
const manualForm = reactive({ title: '', content: '' })

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data
}

const loadDocuments = async () => {
  if (!selectedKb.value) return
  const { data } = await api.listKnowledgeDocuments(selectedKb.value.id)
  documents.value = data
  syncIndexPolling()
}

const createKb = async () => {
  await api.createKnowledgeBase(kbForm)
  showCreateKb.value = false
  ElMessage.success('知识库创建成功')
  await loadKnowledgeBases()
}

const selectKb = async (row: any) => {
  selectedKb.value = row
  await loadDocuments()
}

const onFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
}

const uploadFile = async () => {
  if (!selectedKb.value || !selectedFile.value) return
  const form = new FormData()
  form.append('file', selectedFile.value)
  await api.uploadKnowledgeFile(selectedKb.value.id, form)
  selectedFile.value = null
  ElMessage.success('上传成功，已进入后台向量化队列')
  await loadDocuments()
}

const addManual = async () => {
  if (!selectedKb.value) return
  await api.addKnowledgeContent(selectedKb.value.id, manualForm)
  showManual.value = false
  ElMessage.success('知识内容已添加，已进入后台向量化队列')
  await loadDocuments()
}

const changeVisibility = async (row: any, visibility: string) => {
  if (!selectedKb.value) return
  await api.updateKnowledgeVisibility(selectedKb.value.id, row.id, { visibility })
  ElMessage.success('可见性已更新')
  await loadDocuments()
}

const reindexKb = async () => {
  if (!selectedKb.value) return
  await api.reindexKnowledgeBase(selectedKb.value.id)
  ElMessage.success('已提交重建任务，后台处理中')
  await loadDocuments()
}

const removeDocument = async (row: any) => {
  if (!selectedKb.value) return
  await ElMessageBox.confirm(`确定删除文档“${row.title}”吗？`, '删除确认', { type: 'warning' })
  await api.deleteKnowledgeDocument(selectedKb.value.id, row.id)
  ElMessage.success('文档已删除')
  await loadDocuments()
}

const removeKb = async (row: any) => {
  await ElMessageBox.confirm(`确定删除知识库“${row.name}”吗？`, '删除确认', { type: 'warning' })
  await api.deleteKnowledgeBase(row.id)
  if (selectedKb.value?.id === row.id) {
    selectedKb.value = null
    documents.value = []
  }
  ElMessage.success('知识库已删除')
  await loadKnowledgeBases()
}

const loadPackBindings = async () => {
  if (!selectedKb.value) return
  packBindingLoading.value = true
  try {
    const [bindingRes, packRes] = await Promise.all([
      api.listKnowledgeBaseDictionaryPacks(selectedKb.value.id),
      api.listDictionaryPacks()
    ])
    packBindings.value = bindingRes.data || []
    allPacks.value = packRes.data || []
  } finally {
    packBindingLoading.value = false
  }
}

const openPackBinding = async () => {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  showPackBinding.value = true
  await loadPackBindings()
}

const bindSelectedPack = async () => {
  if (!selectedKb.value || !packToAdd.value) {
    ElMessage.warning('请选择词典包')
    return
  }
  await api.bindKnowledgeBaseDictionaryPack(selectedKb.value.id, packToAdd.value, {
    priority: packPriority.value,
    enabled: true
  })
  ElMessage.success('词典包已引入')
  await loadPackBindings()
}

const toggleBinding = async (row: any) => {
  if (!selectedKb.value) return
  await api.bindKnowledgeBaseDictionaryPack(selectedKb.value.id, row.packId, {
    priority: row.priority,
    enabled: !row.enabled
  })
  ElMessage.success('状态已更新')
  await loadPackBindings()
}

const removeBinding = async (row: any) => {
  if (!selectedKb.value) return
  await api.unbindKnowledgeBaseDictionaryPack(selectedKb.value.id, row.packId)
  ElMessage.success('已移除')
  await loadPackBindings()
}

const hasRunningIndexTask = () =>
  documents.value.some((d: any) => d.indexStatus === 'PENDING' || d.indexStatus === 'RUNNING')

const stopIndexPolling = () => {
  if (pollingTimer.value !== null) {
    window.clearInterval(pollingTimer.value)
    pollingTimer.value = null
  }
}

const startIndexPolling = () => {
  if (pollingTimer.value !== null) {
    return
  }
  pollingTimer.value = window.setInterval(async () => {
    if (!selectedKb.value) {
      stopIndexPolling()
      return
    }
    const { data } = await api.listKnowledgeDocuments(selectedKb.value.id)
    documents.value = data
    if (!hasRunningIndexTask()) {
      stopIndexPolling()
    }
  }, 3000)
}

const syncIndexPolling = () => {
  if (hasRunningIndexTask()) {
    startIndexPolling()
  } else {
    stopIndexPolling()
  }
}

const indexStatusTagType = (status?: string) => {
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  if (status === 'PENDING') return 'info'
  return 'success'
}

onMounted(loadKnowledgeBases)
onBeforeUnmount(() => stopIndexPolling())
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.actions-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.upload-tip {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.index-message {
  color: #64748b;
  font-size: 12px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
