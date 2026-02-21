<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">导出记录</div>
        <div>
          <el-select v-model="format" placeholder="选择格式" style="width: 120px; margin-right: 8px;">
            <el-option label="Word" value="docx" />
            <el-option label="PDF" value="pdf" />
          </el-select>
          <el-input v-model="versionNo" placeholder="版本号，如 V1.0.0" style="width: 180px; margin-right: 8px;" />
          <el-button type="primary" @click="exportDoc">导出</el-button>
        </div>
      </div>
      <el-table :data="exports" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="versionNo" label="版本号" width="120" />
        <el-table-column prop="format" label="格式" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="220" />
        <el-table-column prop="filePath" label="路径" />
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button size="small" :disabled="scope.row.status !== 'SUCCESS'" @click="download(scope.row)">下载</el-button>
            <el-button size="small" type="danger" @click="removeExport(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const exports = ref<any[]>([])
const format = ref('docx')
const versionNo = ref('')

const load = async () => {
  const docId = Number(route.params.docId)
  const { data } = await api.listExports(docId)
  exports.value = data
}

const exportDoc = async () => {
  const docId = Number(route.params.docId)
  if (!versionNo.value.trim()) {
    ElMessage.warning('请输入版本号')
    return
  }
  const { data } = await api.exportDocument(docId, { format: format.value, versionNo: versionNo.value.trim() })
  if (data?.status === 'FAILED') {
    ElMessage.error(`导出失败：${data?.errorMessage || '未知错误'}`)
  } else {
    ElMessage.success('导出任务已创建')
  }
  await load()
}

const download = async (row: any) => {
  const res = await api.downloadExport(row.id)
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `document-${row.id}.${row.format}`
  link.click()
  URL.revokeObjectURL(link.href)
}

const removeExport = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认删除该导出记录？', '删除导出记录', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await api.deleteExport(row.id)
    ElMessage.success('导出记录已删除')
    await load()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.response?.data?.message || '删除失败')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>
