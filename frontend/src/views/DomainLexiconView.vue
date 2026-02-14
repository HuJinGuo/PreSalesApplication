<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">领域词典</div>
      </div>
      <div class="row">
        <el-select v-model="knowledgeBaseId" placeholder="选择知识库" style="width: 260px" @change="loadLexicons">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input v-model="form.category" placeholder="类别（如 FAULT）" style="width: 220px" />
        <el-input v-model="form.term" placeholder="术语（如 数据倒挂）" style="width: 260px" />
        <el-switch v-model="form.enabled" active-text="启用" />
        <el-button type="primary" @click="save">{{ editingId ? '保存修改' : '添加词条' }}</el-button>
        <el-button v-if="editingId" @click="cancelEdit">取消</el-button>
      </div>
      <el-table :data="lexicons" style="margin-top: 12px">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="category" label="类别" width="140" />
        <el-table-column prop="term" label="术语" />
        <el-table-column prop="enabled" label="启用" width="90">
          <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="scope">
            <el-button size="small" @click="edit(scope.row)">编辑</el-button>
            <el-button type="danger" size="small" @click="remove(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const knowledgeBases = ref<any[]>([])
const knowledgeBaseId = ref<number>()
const lexicons = ref<any[]>([])
const editingId = ref<number>()
const form = reactive({
  category: '',
  term: '',
  enabled: true
})

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data
  if (!knowledgeBaseId.value && data.length > 0) {
    knowledgeBaseId.value = data[0].id
    await loadLexicons()
  }
}

const loadLexicons = async () => {
  if (!knowledgeBaseId.value) return
  const { data } = await api.listDomainLexicons(knowledgeBaseId.value)
  lexicons.value = data
}

const save = async () => {
  if (!knowledgeBaseId.value || !form.category.trim() || !form.term.trim()) return
  const payload = {
    knowledgeBaseId: knowledgeBaseId.value,
    category: form.category,
    term: form.term,
    enabled: form.enabled
  }
  if (editingId.value) {
    await api.updateDomainLexicon(editingId.value, payload)
  } else {
    await api.upsertDomainLexicon(payload)
  }
  form.term = ''
  if (!editingId.value) {
    form.category = ''
  }
  editingId.value = undefined
  ElMessage.success('词典已保存')
  await loadLexicons()
}

const remove = async (id: number) => {
  await api.deleteDomainLexicon(id)
  ElMessage.success('词典已删除')
  await loadLexicons()
}

const edit = (row: any) => {
  editingId.value = row.id
  form.category = row.category
  form.term = row.term
  form.enabled = !!row.enabled
}

const cancelEdit = () => {
  editingId.value = undefined
  form.category = ''
  form.term = ''
  form.enabled = true
}

onMounted(loadKnowledgeBases)
</script>

<style scoped>
.header {
  margin-bottom: 10px;
}

.row {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
