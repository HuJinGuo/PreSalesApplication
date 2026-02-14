<template>
  <div class="page">
    <div class="card">
      <div class="header-title">审核中心</div>
      <el-form :inline="true" :model="query" class="search">
        <el-form-item label="章节ID">
          <el-input v-model="query.sectionId" />
        </el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form>
      <el-table :data="reviews" style="width: 100%">
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="comment" label="意见" />
        <el-table-column prop="reviewedBy" label="审核人" width="120" />
        <el-table-column prop="reviewedAt" label="时间" width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api } from '@/api'

const query = reactive({
  sectionId: ''
})
const reviews = ref<any[]>([])

const load = async () => {
  if (!query.sectionId) return
  const { data } = await api.listReviews(Number(query.sectionId))
  reviews.value = data
}
</script>

<style scoped>
.search {
  margin-bottom: 12px;
}
</style>
