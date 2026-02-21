<template>
  <div class="page lexicon-page">
    <div class="card split-left">
      <div class="header">
        <div>
          <div class="header-title">领域词典中心</div>
          <div class="header-sub">维护全局词典包 + 批量编辑 + 同义词归一</div>
        </div>
        <el-button type="primary" @click="openCreatePack">新建词典包</el-button>
      </div>

      <el-table :data="packs" highlight-current-row @current-change="onPackSelect" v-loading="packLoading" row-key="id" height="360">
        <el-table-column prop="name" label="词典包" min-width="180" />
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="scopeType" label="范围" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openEditPack(scope.row)">编辑</el-button>
            <el-button link type="danger" @click.stop="removePack(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card split-right" v-if="selectedPack">
      <div class="header">
        <div>
          <div class="header-title">词条管理：{{ selectedPack.name }}</div>
          <div class="header-sub">term -> standard_term 归一映射</div>
        </div>
      </div>

      <el-tabs v-model="entryTab">
        <el-tab-pane label="表格维护" name="table">
          <div class="entry-toolbar">
            <el-input v-model="entryForm.category" placeholder="类别（如 INSTRUMENT）" style="width: 180px" />
            <el-input v-model="entryForm.term" placeholder="术语（term）" style="width: 220px" />
            <el-input v-model="entryForm.standardTerm" placeholder="标准术语（可空）" style="width: 220px" />
            <el-switch v-model="entryForm.enabled" active-text="启用" />
            <el-button type="primary" @click="saveEntry">{{ editingEntryId ? '保存修改' : '添加词条' }}</el-button>
            <el-button v-if="editingEntryId" @click="cancelEditEntry">取消</el-button>
          </div>

          <el-table :data="entries" border v-loading="entryLoading" height="300">
            <el-table-column prop="category" label="类别" width="140" />
            <el-table-column prop="term" label="术语" min-width="180" />
            <el-table-column prop="standardTerm" label="标准术语" min-width="180" />
            <el-table-column prop="enabled" label="启用" width="90">
              <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="scope">
                <el-button link type="primary" @click="editEntry(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="removeEntry(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="批量编辑" name="batch">
          <el-alert title="支持两种格式：TEXT（[分类]\n术语,标准术语）/ JSON（[{category,term,standardTerm}]）" type="info" :closable="false" />
          <el-input
            v-model="batch.content"
            type="textarea"
            :rows="13"
            resize="none"
            placeholder="[设备]\n红外光谱仪, 气体分析仪\n视频探头, IPC摄像机"
            style="margin-top: 10px"
          />
          <div class="entry-toolbar" style="margin-top: 10px">
            <el-select v-model="batch.format" style="width: 120px">
              <el-option label="TEXT" value="TEXT" />
              <el-option label="JSON" value="JSON" />
            </el-select>
            <el-button type="primary" @click="submitBatch">批量入库</el-button>
            <span class="batch-result" v-if="batchResult">成功 {{ batchResult.success }}/{{ batchResult.total }}，失败 {{ batchResult.failed }}</span>
          </div>
          <div v-if="batchResult?.errors?.length" class="batch-errors">
            <div v-for="(err, idx) in batchResult.errors" :key="idx">{{ err }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="card kb-card">
      <div class="header">
        <div>
          <div class="header-title">知识库词典激活</div>
          <div class="header-sub">为知识库引入全局词典包，并维护本地私有词条</div>
        </div>
      </div>

      <div class="kb-topbar">
        <el-select v-model="knowledgeBaseId" placeholder="选择知识库" class="field-kb" @change="onKbChanged">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-select v-model="packToBind" clearable placeholder="选择词典包" class="field-pack">
          <el-option v-for="p in packs" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-input-number v-model="bindPriority" :min="1" :max="999" controls-position="right" />
        <el-button type="primary" @click="bindPackToKb">引入词典包</el-button>
      </div>

      <div class="kb-workspace">
        <div class="kb-bind-panel">
          <div class="panel-title">已引入词典包</div>
          <el-table :data="kbPackBindings" border v-loading="kbPackLoading" max-height="320">
            <el-table-column prop="packName" label="词典包" min-width="180" />
            <el-table-column prop="packCode" label="编码" width="120" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column prop="enabled" label="启用" width="90">
              <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <el-button link type="primary" @click="toggleKbPack(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button>
                <el-button link type="danger" @click="unbindPack(scope.row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="kb-local-panel" v-if="knowledgeBaseId">
          <div class="panel-title">知识库本地词条（独家术语）</div>
          <div class="entry-toolbar">
            <el-input v-model="localForm.category" placeholder="类别" class="field-mini" />
            <el-input v-model="localForm.term" placeholder="术语" class="field-mid" />
            <el-input v-model="localForm.standardTerm" placeholder="标准术语" class="field-mid" />
            <el-switch v-model="localForm.enabled" active-text="启用" />
            <el-button type="primary" @click="saveLocal">{{ editingLocalId ? '保存' : '新增' }}</el-button>
            <el-button v-if="editingLocalId" @click="cancelLocal">取消</el-button>
          </div>
          <el-table :data="localLexicons" border max-height="320">
            <el-table-column prop="category" label="类别" width="120" />
            <el-table-column prop="term" label="术语" min-width="160" />
            <el-table-column prop="standardTerm" label="标准术语" min-width="160" />
            <el-table-column prop="enabled" label="启用" width="80">
              <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="scope">
                <el-button link type="primary" @click="editLocal(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="removeLocal(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <el-dialog v-model="packDialogVisible" :title="editingPackId ? '编辑词典包' : '新建词典包'" width="520px">
      <el-form :model="packForm" label-width="92px">
        <el-form-item label="编码"><el-input v-model="packForm.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="packForm.name" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="packForm.scopeType" style="width: 100%">
            <el-option label="GLOBAL" value="GLOBAL" />
            <el-option label="PRIVATE" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="packForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="packForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePack">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const packs = ref<any[]>([])
const packLoading = ref(false)
const selectedPack = ref<any>()
const entries = ref<any[]>([])
const entryLoading = ref(false)
const entryTab = ref('table')

const knowledgeBases = ref<any[]>([])
const knowledgeBaseId = ref<number>()
const kbPackBindings = ref<any[]>([])
const kbPackLoading = ref(false)
const packToBind = ref<number>()
const bindPriority = ref(100)

const localLexicons = ref<any[]>([])
const editingLocalId = ref<number>()
const localForm = reactive({ category: '', term: '', standardTerm: '', enabled: true })

const editingEntryId = ref<number>()
const entryForm = reactive({ category: '', term: '', standardTerm: '', enabled: true })

const batch = reactive({ format: 'TEXT', content: '' })
const batchResult = ref<any>()

const packDialogVisible = ref(false)
const editingPackId = ref<number>()
const packForm = reactive({ code: '', name: '', scopeType: 'GLOBAL', status: 'ACTIVE', description: '' })

const loadPacks = async () => {
  packLoading.value = true
  try {
    const { data } = await api.listDictionaryPacks()
    packs.value = data || []
    if (!selectedPack.value && packs.value.length) {
      selectedPack.value = packs.value[0]
      await loadEntries()
    }
  } finally {
    packLoading.value = false
  }
}

const loadEntries = async () => {
  if (!selectedPack.value) return
  entryLoading.value = true
  try {
    const { data } = await api.listDictionaryEntries(selectedPack.value.id)
    entries.value = data || []
  } finally {
    entryLoading.value = false
  }
}

const onPackSelect = async (row: any) => {
  selectedPack.value = row
  editingEntryId.value = undefined
  batchResult.value = undefined
  await loadEntries()
}

const saveEntry = async () => {
  if (!selectedPack.value || !entryForm.category.trim() || !entryForm.term.trim()) {
    ElMessage.warning('请填写类别和术语')
    return
  }
  const payload = { ...entryForm }
  if (editingEntryId.value) {
    await api.updateDictionaryEntry(editingEntryId.value, payload)
  } else {
    await api.upsertDictionaryEntry(selectedPack.value.id, payload)
  }
  ElMessage.success('词条已保存')
  cancelEditEntry()
  await loadEntries()
}

const editEntry = (row: any) => {
  editingEntryId.value = row.id
  entryForm.category = row.category || ''
  entryForm.term = row.term || ''
  entryForm.standardTerm = row.standardTerm || ''
  entryForm.enabled = !!row.enabled
}

const cancelEditEntry = () => {
  editingEntryId.value = undefined
  entryForm.category = ''
  entryForm.term = ''
  entryForm.standardTerm = ''
  entryForm.enabled = true
}

const removeEntry = async (row: any) => {
  await ElMessageBox.confirm(`确认删除词条“${row.term}”？`, '提示', { type: 'warning' })
  await api.deleteDictionaryEntry(row.id)
  ElMessage.success('已删除')
  await loadEntries()
}

const submitBatch = async () => {
  if (!selectedPack.value || !batch.content.trim()) {
    ElMessage.warning('请先输入批量内容')
    return
  }
  const { data } = await api.batchUpsertDictionaryEntries(selectedPack.value.id, {
    format: batch.format,
    content: batch.content
  })
  batchResult.value = data
  ElMessage.success(`处理完成：成功${data.success}条`) 
  await loadEntries()
}

const openCreatePack = () => {
  editingPackId.value = undefined
  packForm.code = ''
  packForm.name = ''
  packForm.scopeType = 'GLOBAL'
  packForm.status = 'ACTIVE'
  packForm.description = ''
  packDialogVisible.value = true
}

const openEditPack = (row: any) => {
  editingPackId.value = row.id
  packForm.code = row.code
  packForm.name = row.name
  packForm.scopeType = row.scopeType || 'GLOBAL'
  packForm.status = row.status || 'ACTIVE'
  packForm.description = row.description || ''
  packDialogVisible.value = true
}

const savePack = async () => {
  if (!packForm.code.trim() || !packForm.name.trim()) {
    ElMessage.warning('请填写词典包编码和名称')
    return
  }
  if (editingPackId.value) {
    await api.updateDictionaryPack(editingPackId.value, packForm)
  } else {
    await api.createDictionaryPack(packForm)
  }
  packDialogVisible.value = false
  ElMessage.success('词典包已保存')
  await loadPacks()
}

const removePack = async (row: any) => {
  await ElMessageBox.confirm(`确认删除词典包“${row.name}”？`, '提示', { type: 'warning' })
  await api.deleteDictionaryPack(row.id)
  ElMessage.success('词典包已删除')
  if (selectedPack.value?.id === row.id) {
    selectedPack.value = undefined
    entries.value = []
  }
  await loadPacks()
}

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data || []
  if (!knowledgeBaseId.value && knowledgeBases.value.length) {
    knowledgeBaseId.value = knowledgeBases.value[0].id
  }
}

const loadKbPackBindings = async () => {
  if (!knowledgeBaseId.value) return
  kbPackLoading.value = true
  try {
    const { data } = await api.listKnowledgeBaseDictionaryPacks(knowledgeBaseId.value)
    kbPackBindings.value = data || []
  } finally {
    kbPackLoading.value = false
  }
}

const loadLocalLexicons = async () => {
  if (!knowledgeBaseId.value) return
  const { data } = await api.listDomainLexicons(knowledgeBaseId.value)
  localLexicons.value = data || []
}

const onKbChanged = async () => {
  await Promise.all([loadKbPackBindings(), loadLocalLexicons()])
}

const bindPackToKb = async () => {
  if (!knowledgeBaseId.value || !packToBind.value) {
    ElMessage.warning('请选择知识库和词典包')
    return
  }
  await api.bindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, packToBind.value, { priority: bindPriority.value, enabled: true })
  ElMessage.success('已引入词典包')
  await loadKbPackBindings()
}

const toggleKbPack = async (row: any) => {
  if (!knowledgeBaseId.value) return
  await api.bindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, row.packId, {
    priority: row.priority,
    enabled: !row.enabled
  })
  ElMessage.success('状态已更新')
  await loadKbPackBindings()
}

const unbindPack = async (row: any) => {
  if (!knowledgeBaseId.value) return
  await api.unbindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, row.packId)
  ElMessage.success('已移除词典包')
  await loadKbPackBindings()
}

const saveLocal = async () => {
  if (!knowledgeBaseId.value || !localForm.category.trim() || !localForm.term.trim()) {
    ElMessage.warning('请填写本地词条信息')
    return
  }
  const payload = {
    knowledgeBaseId: knowledgeBaseId.value,
    category: localForm.category,
    term: localForm.term,
    standardTerm: localForm.standardTerm,
    enabled: localForm.enabled
  }
  if (editingLocalId.value) {
    await api.updateDomainLexicon(editingLocalId.value, payload)
  } else {
    await api.upsertDomainLexicon(payload)
  }
  cancelLocal()
  ElMessage.success('本地词条已保存')
  await loadLocalLexicons()
}

const editLocal = (row: any) => {
  editingLocalId.value = row.id
  localForm.category = row.category || ''
  localForm.term = row.term || ''
  localForm.standardTerm = row.standardTerm || ''
  localForm.enabled = !!row.enabled
}

const cancelLocal = () => {
  editingLocalId.value = undefined
  localForm.category = ''
  localForm.term = ''
  localForm.standardTerm = ''
  localForm.enabled = true
}

const removeLocal = async (row: any) => {
  await api.deleteDomainLexicon(row.id)
  ElMessage.success('本地词条已删除')
  await loadLocalLexicons()
}

onMounted(async () => {
  await Promise.all([loadPacks(), loadKnowledgeBases()])
  await onKbChanged()
})
</script>

<style scoped>
.lexicon-page {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.kb-card {
  grid-column: 1 / -1;
}

.split-left,
.split-right {
  min-height: 460px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.header-title {
  font-size: 17px;
  font-weight: 700;
  color: #1f2a3d;
}

.header-sub {
  font-size: 12px;
  color: #64748b;
}

.entry-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.field-kb {
  width: 280px;
}

.field-pack {
  width: 260px;
}

.field-mini {
  width: 130px;
}

.field-mid {
  width: 180px;
}

.kb-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.kb-workspace {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 14px;
}

.kb-bind-panel,
.kb-local-panel {
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  padding: 10px;
  background: #fafcff;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.batch-result {
  color: #2563eb;
  font-size: 13px;
}

.batch-errors {
  margin-top: 8px;
  font-size: 12px;
  color: #b91c1c;
  max-height: 120px;
  overflow: auto;
  border: 1px solid #fee2e2;
  border-radius: 8px;
  background: #fff5f5;
  padding: 8px;
}

@media (max-width: 1200px) {
  .lexicon-page {
    grid-template-columns: 1fr;
  }

  .kb-workspace {
    grid-template-columns: 1fr;
  }
}
</style>
