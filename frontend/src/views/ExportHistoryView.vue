<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">导出记录</div>
        <div>
          <el-select v-model="format" placeholder="选择格式">
            <el-option label="DOCX" value="docx" />
            <el-option label="PDF" value="pdf" />
          </el-select>
          <el-button type="primary" @click="exportDoc">导出</el-button>
        </div>
      </div>
      <el-table :data="exports" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="format" label="格式" width="100" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="filePath" label="路径" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" @click="download(scope.row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const exports = ref<any[]>([])
const format = ref('docx')

const load = async () => {
  const docId = Number(route.params.docId)
  const { data } = await api.listExports(docId)
  exports.value = data
}

const exportDoc = async () => {
  const docId = Number(route.params.docId)
  await api.exportDocument(docId, { format: format.value })
  ElMessage.success('导出任务已创建')
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
