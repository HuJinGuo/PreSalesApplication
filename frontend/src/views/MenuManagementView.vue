<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div>
          <div class="header-title">菜单管理</div>
          <div class="header-sub">控制左侧导航层级、排序与是否显示</div>
        </div>
        <el-button type="primary" @click="openCreate">新增菜单</el-button>
      </div>

      <el-table :data="menus" border row-key="id" default-expand-all v-loading="loading">
        <el-table-column prop="title" label="菜单名称" min-width="220" />
        <el-table-column prop="path" label="路由路径" min-width="200" />
        <el-table-column prop="icon" label="图标" width="140" />
        <el-table-column prop="sortIndex" label="排序" width="90" />
        <el-table-column prop="visible" label="显示" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.visible ? 'success' : 'info'">{{ scope.row.visible ? '是' : '否' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑菜单' : '新增菜单'" width="520px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="父菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'title', value: 'id', children: 'children' }"
            node-key="id"
            clearable
            check-strictly
            style="width: 100%"
            placeholder="根菜单"
          />
        </el-form-item>
        <el-form-item label="菜单名称"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="路由路径"><el-input v-model="form.path" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="例如：Folder" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortIndex" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="是否显示"><el-switch v-model="form.visible" /></el-form-item>
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
const menus = ref<any[]>([])
const menuFlat = ref<any[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<any>({ id: 0, parentId: undefined, title: '', path: '', icon: '', sortIndex: 0, visible: true })

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

const parentOptions = computed(() => {
  if (!editingId.value) return menus.value
  const blockId = editingId.value
  const blockIds = new Set<number>([blockId])
  const map = new Map<number, any>()
  for (const item of menuFlat.value) {
    map.set(item.id, item)
  }
  const walkChildren = (id: number) => {
    for (const item of menuFlat.value.filter((it) => it.parentId === id)) {
      if (!blockIds.has(item.id)) {
        blockIds.add(item.id)
        walkChildren(item.id)
      }
    }
  }
  walkChildren(blockId)
  const clone = (nodes: any[]): any[] => {
    const list: any[] = []
    for (const node of nodes || []) {
      if (blockIds.has(node.id)) continue
      list.push({ ...node, children: clone(node.children || []) })
    }
    return list
  }
  return clone(menus.value)
})

const resetForm = () => {
  form.id = 0
  form.parentId = undefined
  form.title = ''
  form.path = ''
  form.icon = ''
  form.sortIndex = 0
  form.visible = true
}

const loadMenus = async () => {
  loading.value = true
  try {
    const { data } = await api.listMenus()
    menus.value = data || []
    menuFlat.value = flattenTree(menus.value)
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
  form.id = row.id
  form.parentId = row.parentId
  form.title = row.title
  form.path = row.path
  form.icon = row.icon
  form.sortIndex = row.sortIndex
  form.visible = row.visible
  dialogVisible.value = true
}

const save = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请填写菜单名称')
    return
  }
  const payload = {
    parentId: form.parentId,
    title: form.title,
    path: form.path,
    icon: form.icon,
    sortIndex: form.sortIndex,
    visible: form.visible
  }
  if (editingId.value) {
    await api.updateMenu(editingId.value, payload)
  } else {
    await api.createMenu(payload)
  }
  dialogVisible.value = false
  ElMessage.success('菜单已保存')
  await loadMenus()
}

const remove = async (row: any) => {
  await ElMessageBox.confirm(`确认删除菜单“${row.title}”？`, '提示', { type: 'warning' })
  await api.deleteMenu(row.id)
  ElMessage.success('菜单已删除')
  await loadMenus()
}

onMounted(loadMenus)
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
