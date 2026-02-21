<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div>
          <div class="header-title">部门管理</div>
          <div class="header-sub">维护组织结构与负责人信息</div>
        </div>
        <el-button type="primary" @click="openCreate">新增部门</el-button>
      </div>

      <el-table :data="departments" border v-loading="loading">
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="code" label="部门编码" width="160" />
        <el-table-column prop="managerName" label="负责人" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑部门' : '新增部门'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="部门名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.managerName" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const loading = ref(false)
const departments = ref<any[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ code: '', name: '', managerName: '', status: 'ACTIVE' })

const resetForm = () => {
  form.code = ''
  form.name = ''
  form.managerName = ''
  form.status = 'ACTIVE'
}

const loadDepartments = async () => {
  loading.value = true
  try {
    const { data } = await api.listDepartments()
    departments.value = data || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.managerName = row.managerName || ''
  form.status = row.status || 'ACTIVE'
  dialogVisible.value = true
}

const save = async () => {
  if (!form.name.trim() || !form.code.trim()) {
    ElMessage.warning('请填写完整部门信息')
    return
  }
  if (editingId.value) {
    await api.updateDepartment(editingId.value, form)
  } else {
    await api.createDepartment(form)
  }
  dialogVisible.value = false
  ElMessage.success('已保存')
  await loadDepartments()
}

const remove = async (row: any) => {
  await ElMessageBox.confirm(`确认删除部门“${row.name}”？`, '提示', { type: 'warning' })
  await api.deleteDepartment(row.id)
  ElMessage.success('已删除')
  await loadDepartments()
}

onMounted(loadDepartments)
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2a3d;
}

.header-sub {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}
</style>
