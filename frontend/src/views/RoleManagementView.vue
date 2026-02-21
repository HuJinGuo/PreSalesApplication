<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div>
          <div class="header-title">角色管理</div>
          <div class="header-sub">角色控制菜单权限，用户可绑定多个角色</div>
        </div>
        <el-button type="primary" @click="openCreate">新增角色</el-button>
      </div>

      <el-table :data="roles" border v-loading="loading">
        <el-table-column prop="code" label="角色编码" width="180" />
        <el-table-column prop="name" label="角色名称" width="180" />
        <el-table-column label="菜单权限">
          <template #default="scope">
            <div class="menu-tags">
              <el-tag v-for="id in scope.row.menuIds || []" :key="id" size="small" type="info" effect="plain">
                {{ menuNameMap[id] || `菜单#${id}` }}
              </el-tag>
              <span v-if="!(scope.row.menuIds || []).length" class="empty-text">未配置</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="640px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="菜单权限">
          <el-tree-select
            v-model="form.menuIds"
            :data="menuTree"
            :props="{ label: 'title', value: 'id', children: 'children' }"
            node-key="id"
            multiple
            check-strictly
            show-checkbox
            default-expand-all
            style="width: 100%"
            clearable
          />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const loading = ref(false)
const roles = ref<any[]>([])
const menuTree = ref<any[]>([])
const menuFlat = ref<any[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  code: '',
  name: '',
  menuIds: [] as number[]
})

const menuNameMap = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  for (const item of menuFlat.value) {
    map[item.id] = item.title
  }
  return map
})

const flattenTree = (nodes: any[]) => {
  const out: any[] = []
  const walk = (list: any[]) => {
    for (const node of list || []) {
      out.push(node)
      if (node.children?.length) walk(node.children)
    }
  }
  walk(nodes)
  return out
}

const loadMenus = async () => {
  const { data } = await api.listMenus()
  menuTree.value = data || []
  menuFlat.value = flattenTree(menuTree.value)
}

const loadRoles = async () => {
  loading.value = true
  try {
    const { data } = await api.listRoles()
    roles.value = data || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.code = ''
  form.name = ''
  form.menuIds = []
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  form.menuIds = [...(row.menuIds || [])]
  dialogVisible.value = true
}

const save = async () => {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  const payload = {
    code: form.code,
    name: form.name,
    menuIds: form.menuIds
  }
  if (editingId.value) {
    await api.updateRole(editingId.value, payload)
  } else {
    await api.createRole(payload)
  }
  dialogVisible.value = false
  ElMessage.success('角色已保存')
  await loadRoles()
}

const remove = async (row: any) => {
  await ElMessageBox.confirm(`确认删除角色“${row.name}”？`, '提示', { type: 'warning' })
  await api.deleteRole(row.id)
  ElMessage.success('角色已删除')
  await loadRoles()
}

onMounted(async () => {
  await Promise.all([loadMenus(), loadRoles()])
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

.menu-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-text {
  color: #94a3b8;
}
</style>
