<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">章节资产库</div>
      </div>
      <el-form :inline="true" :model="query" class="search">
        <el-form-item label="行业">
          <el-input v-model="query.industry" />
        </el-form-item>
        <el-form-item label="范围">
          <el-input v-model="query.scope" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" />
        </el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
      </el-form>
      <el-table :data="assets" style="width: 100%">
        <el-table-column prop="id" label="资产ID" width="80" />
        <el-table-column prop="industryTag" label="行业" width="120" />
        <el-table-column prop="scopeTag" label="范围" />
        <el-table-column prop="isWinning" label="中标" width="80" />
        <el-table-column prop="keywords" label="关键词" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" @click="openReuse(scope.row)">复用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showReuse" title="复用资产" width="480px">
      <el-form :model="reuseForm">
        <el-form-item label="文档ID">
          <el-input v-model="reuseForm.documentId" />
        </el-form-item>
        <el-form-item label="父章节ID">
          <el-input v-model="reuseForm.targetParentId" />
        </el-form-item>
        <el-form-item label="层级">
          <el-input-number v-model="reuseForm.targetLevel" :min="1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="reuseForm.targetSortIndex" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReuse = false">取消</el-button>
        <el-button type="primary" @click="reuse">复用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const assets = ref<any[]>([])
const showReuse = ref(false)
const selectedAsset = ref<any>(null)

const query = reactive({
  industry: '',
  scope: '',
  keyword: ''
})

const reuseForm = reactive({
  assetId: '',
  targetParentId: '',
  targetSortIndex: 1,
  targetLevel: 1,
  documentId: ''
})

const search = async () => {
  const { data } = await api.searchAssets(query)
  assets.value = data
}

const openReuse = (row: any) => {
  selectedAsset.value = row
  reuseForm.assetId = row.id
  showReuse.value = true
}

const reuse = async () => {
  try {
    await api.reuseSection(Number(reuseForm.targetParentId), {
      assetId: Number(reuseForm.assetId),
      targetParentId: reuseForm.targetParentId ? Number(reuseForm.targetParentId) : null,
      targetSortIndex: Number(reuseForm.targetSortIndex),
      targetLevel: Number(reuseForm.targetLevel),
      documentId: Number(reuseForm.documentId)
    })
    ElMessage.success('复用成功')
    showReuse.value = false
  } catch (err) {
    ElMessage.error('复用失败')
  }
}
</script>

<style scoped>
.search {
  margin-bottom: 12px;
}
</style>
