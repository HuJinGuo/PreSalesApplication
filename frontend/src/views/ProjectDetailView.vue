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
      <el-table :data="documents" style="width: 100%">
        <el-table-column prop="name" label="文档名称" />
        <el-table-column prop="docType" label="类型" width="160" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" @click="openDoc(scope.row)">编辑</el-button>
            <el-button size="small" @click="openExports(scope.row)">导出</el-button>
          </template>
        </el-table-column>
      </el-table>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const router = useRouter()
const project = ref<any>(null)
const documents = ref<any[]>([])
const showCreate = ref(false)

const docForm = reactive({
  name: '',
  docType: ''
})

const load = async () => {
  const id = Number(route.params.id)
  const projectRes = await api.getProject(id)
  project.value = projectRes.data
  const docsRes = await api.listDocuments(id)
  documents.value = docsRes.data
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

const openExports = (doc: any) => {
  router.push(`/exports/${doc.id}`)
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
</style>
