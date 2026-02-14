<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">项目管理</div>
        <el-button type="primary" @click="showCreate = true">新建项目</el-button>
      </div>
      <el-table :data="projects" style="width: 100%">
        <el-table-column prop="code" label="项目编号" width="160" />
        <el-table-column prop="name" label="项目名称" />
        <el-table-column prop="customerName" label="客户" />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="scale" label="规模" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" @click="goDetail(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreate" title="新建项目" width="480px">
      <el-form :model="form">
        <el-form-item label="项目编号">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="项目名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="form.customerName" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="form.industry" />
        </el-form-item>
        <el-form-item label="规模">
          <el-input v-model="form.scale" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="createProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const router = useRouter()
const projects = ref<any[]>([])
const showCreate = ref(false)
const form = reactive({
  code: '',
  name: '',
  customerName: '',
  industry: '',
  scale: ''
})

const load = async () => {
  const { data } = await api.listProjects()
  projects.value = data
}

const createProject = async () => {
  try {
    await api.createProject(form)
    ElMessage.success('创建成功')
    showCreate.value = false
    await load()
  } catch (err) {
    ElMessage.error('创建失败')
  }
}

const goDetail = (row: any) => {
  router.push(`/projects/${row.id}`)
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
