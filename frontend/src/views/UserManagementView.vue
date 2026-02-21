<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div>
          <div class="header-title">用户管理</div>
          <div class="header-sub">用户可关联部门、角色以及补充菜单权限</div>
        </div>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>

      <el-table :data="users" border v-loading="loading">
        <el-table-column prop="username" label="用户名" min-width="160" />
        <el-table-column prop="realName" label="姓名" width="130" />
        <el-table-column prop="departmentName" label="部门" width="180" />
        <el-table-column label="角色" min-width="180">
          <template #default="scope">
            <el-tag v-for="code in scope.row.roleCodes || []" :key="code" size="small" type="info" effect="plain">{{ code }}</el-tag>
            <span v-if="!(scope.row.roleCodes || []).length" class="empty-text">未配置</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="680px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="editingId ? '不修改请留空' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="form.departmentId" clearable style="width: 100%" placeholder="请选择部门">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定角色">
          <el-select v-model="form.roleIds" multiple clearable style="width: 100%" placeholder="请选择角色">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.name} (${role.code})`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充菜单权限">
          <el-tree-select
            v-model="form.menuIds"
            :data="menus"
            :props="{ label: 'title', value: 'id', children: 'children' }"
            node-key="id"
            multiple
            check-strictly
            show-checkbox
            default-expand-all
            clearable
            style="width: 100%"
            placeholder="可选：追加角色以外的菜单"
          />
        </el-form-item>
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
const users = ref<any[]>([])
const departments = ref<any[]>([])
const roles = ref<any[]>([])
const menus = ref<any[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<any>({
  username: '',
  password: '',
  realName: '',
  departmentId: undefined,
  roleIds: [],
  menuIds: [],
  status: 'ACTIVE'
})

const loadBaseData = async () => {
  const [userRes, deptRes, roleRes, menuRes] = await Promise.all([
    api.listUsersByBase(),
    api.listDepartments(),
    api.listRoles(),
    api.listMenus()
  ])
  users.value = userRes.data || []
  departments.value = deptRes.data || []
  roles.value = roleRes.data || []
  menus.value = menuRes.data || []
}

const loadUsers = async () => {
  loading.value = true
  try {
    const { data } = await api.listUsersByBase()
    users.value = data || []
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.username = ''
  form.password = ''
  form.realName = ''
  form.departmentId = undefined
  form.roleIds = []
  form.menuIds = []
  form.status = 'ACTIVE'
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  editingId.value = row.id
  form.username = row.username
  form.password = ''
  form.realName = row.realName || ''
  form.departmentId = row.departmentId
  form.roleIds = [...(row.roleIds || [])]
  form.menuIds = [...(row.menuIds || [])]
  form.status = row.status || 'ACTIVE'
  dialogVisible.value = true
}

const save = async () => {
  if (!form.username.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!editingId.value && !form.password) {
    ElMessage.warning('新建用户必须设置密码')
    return
  }
  const payload = {
    username: form.username,
    password: form.password,
    realName: form.realName,
    departmentId: form.departmentId,
    roleIds: form.roleIds,
    menuIds: form.menuIds,
    status: form.status
  }
  if (editingId.value) {
    await api.updateBaseUser(editingId.value, payload)
  } else {
    await api.createBaseUser(payload)
  }
  dialogVisible.value = false
  ElMessage.success('用户已保存')
  await loadUsers()
}

const remove = async (row: any) => {
  await ElMessageBox.confirm(`确认删除用户“${row.username}”？`, '提示', { type: 'warning' })
  await api.deleteBaseUser(row.id)
  ElMessage.success('用户已删除')
  await loadUsers()
}

onMounted(async () => {
  loading.value = true
  try {
    await loadBaseData()
  } finally {
    loading.value = false
  }
})
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

.empty-text {
  color: #94a3b8;
}
</style>
