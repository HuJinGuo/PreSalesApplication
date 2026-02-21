<template>
  <div class="page">
    <div class="card" v-if="project">
      <div class="header">
        <div class="header-title">项目：{{ project.name }}</div>
        <el-button @click="back">返回</el-button>
      </div>
      <div class="meta">
        <div>编号：{{ project.code }}</div>
        <div>客户：{{ project.customerName }}</div>
        <div>行业：{{ project.industry }}</div>
        <div>规模：{{ project.scale }}</div>
      </div>
    </div>

    <div class="card" style="margin-top: 16px;">
      <div class="header">
        <div class="header-title">文档列表</div>
        <el-button type="primary" @click="showCreate = true">新建文档</el-button>
      </div>
      <el-table :data="pagedDocuments" style="width: 100%">
        <el-table-column prop="name" label="文档名称" />
        <el-table-column prop="docType" label="类型" width="160" />
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" @click="openDoc(scope.row)">编辑</el-button>
            <el-button size="small" :loading="exportingDocId === scope.row.id" @click="openExportDialog(scope.row)">导出</el-button>
            <el-button size="small" type="danger" @click="deleteDocument(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="docPage"
          v-model:page-size="docPageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="documents.length"
          :page-sizes="[5, 10, 20, 50]"
        />
      </div>
    </div>

    <div class="card" style="margin-top: 16px;">
      <div class="header">
        <div class="header-title">导出记录</div>
      </div>
      <el-table :data="pagedExports" style="width: 100%">
        <el-table-column prop="documentName" label="所属文档" min-width="180" />
        <el-table-column prop="versionNo" label="版本号" width="120" />
        <el-table-column prop="format" label="格式" width="110" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="220" />
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button
              size="small"
              :disabled="scope.row.status !== 'SUCCESS'"
              @click="downloadExport(scope.row)"
            >
              下载
            </el-button>
            <el-button size="small" type="danger" @click="deleteExport(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="exportPage"
          v-model:page-size="exportPageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="exportRows.length"
          :page-sizes="[5, 10, 20, 50]"
        />
      </div>
    </div>

    <el-dialog v-model="showCreate" title="新建文档" width="420px">
      <el-form :model="docForm">
        <el-form-item label="文档名称">
          <el-input v-model="docForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="docForm.docType" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="createDocument">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showExportDialog" title="导出文档" width="480px">
      <el-form :model="exportForm" label-width="84px">
        <el-form-item label="文档">
          <el-input :model-value="exportForm.documentName" disabled />
        </el-form-item>
        <el-form-item label="导出格式">
          <el-select v-model="exportForm.format" style="width: 100%">
            <el-option label="Word (DOCX)" value="docx" />
            <el-option label="PDF" value="pdf" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="exportForm.versionNo" placeholder="如 V1.0.3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExportDialog = false">取消</el-button>
        <el-button type="primary" :loading="exportingDocId === exportForm.documentId" @click="submitExport">确定导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const router = useRouter()
const project = ref<any>(null)
const documents = ref<any[]>([])
const exportRows = ref<any[]>([])
const showCreate = ref(false)
const showExportDialog = ref(false)
const docPage = ref(1)
const docPageSize = ref(10)
const exportPage = ref(1)
const exportPageSize = ref(10)
const exportingDocId = ref<number | null>(null)

const docForm = reactive({
  name: '',
  docType: ''
})
const exportForm = reactive({
  documentId: 0,
  documentName: '',
  format: 'docx',
  versionNo: ''
})
const pagedDocuments = computed(() => {
  const start = (docPage.value - 1) * docPageSize.value
  return documents.value.slice(start, start + docPageSize.value)
})
const pagedExports = computed(() => {
  const start = (exportPage.value - 1) * exportPageSize.value
  return exportRows.value.slice(start, start + exportPageSize.value)
})

const load = async () => {
  const id = Number(route.params.id)
  const projectRes = await api.getProject(id)
  project.value = projectRes.data
  const docsRes = await api.listDocuments(id)
  documents.value = docsRes.data
  docPage.value = 1
  await loadExportsByDocuments()
}
const loadExportsByDocuments = async () => {
  if (!documents.value.length) {
    exportRows.value = []
    exportPage.value = 1
    return
  }
  const settled = await Promise.allSettled(
    documents.value.map(async (doc) => {
      const { data } = await api.listExports(doc.id)
      return (data || []).map((item: any) => ({
        ...item,
        documentId: doc.id,
        documentName: doc.name
      }))
    })
  )
  const merged = settled
    .filter((r): r is PromiseFulfilledResult<any[]> => r.status === 'fulfilled')
    .flatMap(r => r.value)
  merged.sort((a: any, b: any) => {
    const bt = new Date(b.createdAt || b.finishedAt || 0).getTime()
    const at = new Date(a.createdAt || a.finishedAt || 0).getTime()
    return bt - at
  })
  exportRows.value = merged
  exportPage.value = 1
}

const createDocument = async () => {
  try {
    const id = Number(route.params.id)
    await api.createDocument(id, docForm)
    ElMessage.success('创建成功')
    showCreate.value = false
    await load()
  } catch (err) {
    ElMessage.error('创建失败')
  }
}

const openDoc = (doc: any) => {
  router.push(`/documents/${doc.id}/edit`)
}

const openExportDialog = (doc: any) => {
  exportForm.documentId = doc.id
  exportForm.documentName = doc.name
  exportForm.format = 'docx'
  exportForm.versionNo = ''
  showExportDialog.value = true
}

const submitExport = async () => {
  try {
    if (!exportForm.versionNo.trim()) {
      ElMessage.warning('请输入版本号')
      return
    }
    exportingDocId.value = exportForm.documentId
    const { data } = await api.exportDocument(exportForm.documentId, {
      format: exportForm.format,
      versionNo: exportForm.versionNo.trim()
    })
    await loadExportsByDocuments()
    if (data?.status === 'FAILED') {
      ElMessage.error(`导出失败：${data?.errorMessage || '未知错误'}`)
      return
    }
    ElMessage.success(`已创建${exportForm.format.toUpperCase()}导出任务（版本：${exportForm.versionNo.trim()}）`)
    showExportDialog.value = false
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '导出失败')
  } finally {
    exportingDocId.value = null
  }
}

const deleteDocument = async (doc: any) => {
  try {
    await ElMessageBox.confirm(`确认删除文档「${doc.name}」？该操作不可恢复。`, '删除文档', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.deleteDocument(doc.id)
    ElMessage.success('文档已删除')
    await load()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}

const deleteExport = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认删除该导出记录？', '删除导出记录', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.deleteExport(row.id)
    ElMessage.success('导出记录已删除')
    await loadExportsByDocuments()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}

const downloadExport = async (row: any) => {
  const res = await api.downloadExport(row.id)
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `document-${row.documentId || row.id}-${row.id}.${(row.format || 'pdf').toLowerCase()}`
  link.click()
  URL.revokeObjectURL(link.href)
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '-'
  return d.toLocaleString()
}

const back = () => router.push('/projects')

onMounted(load)
</script>

<style scoped>
.meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
