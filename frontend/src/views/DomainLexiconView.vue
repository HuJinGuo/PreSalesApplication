<template>
  <div class="page lexicon-page">
    <div class="card page-header">
      <div>
        <div class="header-title">领域词典中心</div>
        <div class="header-sub">类别与关系可维护，词条标准化，按知识库激活使用</div>
      </div>
    </div>

    <el-tabs v-model="mainTab" class="main-tabs">
       <el-tab-pane label="类别管理" name="categories">
        <div class="card panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="categoryQuery" placeholder="搜索类别编码/名称" clearable style="width: 260px" />
              <el-switch v-model="categoryActiveOnly" inline-prompt active-text="仅启用" inactive-text="全部" />
            </div>
            <div class="toolbar-right">
              <el-button @click="loadCategories">刷新</el-button>
              <el-button type="primary" @click="openCategoryDialog()">新增类别</el-button>
            </div>
          </div>
          <el-table :data="pagedCategories" border height="500">
            <el-table-column prop="code" label="编码" width="170" />
            <el-table-column prop="name" label="名称" min-width="180" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="sortOrder" label="排序" width="100" />
            <el-table-column prop="description" label="描述" min-width="220" />
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button link type="primary" @click="openCategoryDialog(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="removeCategory(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            v-model:current-page="categoryPage"
            v-model:page-size="categoryPageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="filteredCategories.length"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="类别关系" name="relations">
        <div class="card panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="relationQuery" placeholder="搜索类别关系" clearable style="width: 260px" />
              <el-switch v-model="relationActiveOnly" inline-prompt active-text="仅启用" inactive-text="全部" />
            </div>
            <div class="toolbar-right">
              <el-button @click="loadCategoryRelations">刷新</el-button>
              <el-button type="primary" @click="openRelationDialog()">新增关系</el-button>
            </div>
          </div>
          <el-table :data="pagedRelations" border height="500">
            <el-table-column prop="sourceCategoryName" label="源类别" width="180">
              <template #default="scope">{{ scope.row.sourceCategoryName || scope.row.sourceCategory || '-' }}</template>
            </el-table-column>
            <el-table-column prop="targetCategoryName" label="目标类别" width="180">
              <template #default="scope">{{ scope.row.targetCategoryName || scope.row.targetCategory || '-' }}</template>
            </el-table-column>
            <el-table-column prop="relationLabel" label="关系标签" min-width="160" />
            <el-table-column prop="enabled" label="启用" width="90">
              <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button link type="primary" @click="openRelationDialog(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="removeRelation(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            v-model:current-page="relationPage"
            v-model:page-size="relationPageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            :total="filteredRelations.length"
          />
        </div>
      </el-tab-pane>
      <el-tab-pane label="词典包与词条" name="packs">
        <div class="split-2">
          <div class="card panel">
            <div class="toolbar">
              <el-input v-model="packQuery" placeholder="搜索词典包名称/编码" clearable style="width: 220px" />
              <el-button type="primary" @click="openPackDialog()">新建词典包</el-button>
            </div>
            <el-table
              :data="pagedPacks"
              v-loading="packLoading"
              highlight-current-row
              row-key="id"
              @current-change="onPackSelect"
              height="420"
            >
              <el-table-column prop="name" label="词典包" min-width="90" />
              <el-table-column prop="code" label="编码" width="200" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column label="操作" width="150">
                <template #default="scope">
                  <el-button link type="primary" @click.stop="openPackDialog(scope.row)">编辑</el-button>
                  <el-button link type="danger" @click.stop="removePack(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              class="pager"
              v-model:current-page="packPage"
              v-model:page-size="packPageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="filteredPacks.length"
            />
          </div>

          <div class="card panel" v-if="selectedPack">
            <div class="toolbar">
              <div class="panel-title">词条：{{ selectedPack.name }}</div>
              <div class="toolbar-right">
                <el-input v-model="entryQuery" placeholder="搜索类别/术语/标准术语" clearable style="width: 260px" />
                <el-button type="primary" @click="openEntryDialog()">新增词条</el-button>
              </div>
            </div>

            <el-table :data="pagedEntries" border v-loading="entryLoading" height="340">
              <el-table-column prop="categoryName" label="类别" width="140" />
              <el-table-column prop="term" label="术语" min-width="180" />
              <el-table-column prop="standardTerm" label="标准术语" min-width="180" />
              <el-table-column prop="enabled" label="启用" width="90">
                <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="scope">
                  <el-button link type="primary" @click="openEntryDialog(scope.row)">编辑</el-button>
                  <el-button link type="danger" @click="removeEntry(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              class="pager"
              v-model:current-page="entryPage"
              v-model:page-size="entryPageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="filteredEntries.length"
            />

            <el-collapse class="batch-collapse">
              <el-collapse-item title="批量导入" name="batch">
                <el-alert
                  title="支持 TEXT（[分类]\n术语,标准术语）和 JSON（[{category,term,standardTerm}]）"
                  type="info"
                  :closable="false"
                />
                <el-input
                  v-model="batch.content"
                  type="textarea"
                  :rows="8"
                  resize="none"
                  placeholder="[POLLUTANT]\n氨氮,NH3-N\n总磷,TP"
                  style="margin-top: 10px"
                />
                <div class="toolbar" style="margin-top: 10px">
                  <el-select v-model="batch.format" style="width: 120px">
                    <el-option label="TEXT" value="TEXT" />
                    <el-option label="JSON" value="JSON" />
                  </el-select>
                  <el-button type="primary" @click="submitBatch">批量入库</el-button>
                  <span v-if="batchResult" class="batch-result">成功 {{ batchResult.success }}/{{ batchResult.total }}，失败 {{ batchResult.failed }}</span>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div class="card panel empty-panel" v-else>
            <el-empty description="请先选择一个词典包" />
          </div>
        </div>
      </el-tab-pane>

     

      <el-tab-pane label="知识库激活" name="kb">
        <div class="card panel">
          <div class="toolbar wrap">
            <el-select v-model="knowledgeBaseId" placeholder="选择知识库" style="width: 260px" @change="onKbChanged">
              <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <el-select v-model="packToBind" clearable placeholder="选择词典包" style="width: 260px">
              <el-option v-for="p in packs" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
            <el-input-number v-model="bindPriority" :min="1" :max="999" />
            <el-button type="primary" @click="bindPackToKb">引入词典包</el-button>
            <el-button @click="openLocalDialog()" :disabled="!knowledgeBaseId">新增本地词条</el-button>
          </div>

          <div class="split-2" style="margin-top: 10px">
            <div>
              <div class="table-title">已引入词典包</div>
              <el-input v-model="kbPackQuery" placeholder="搜索词典包" clearable style="width: 240px; margin-bottom: 8px" />
              <el-table :data="pagedKbBindings" border v-loading="kbPackLoading" height="340">
                <el-table-column prop="packName" label="词典包" min-width="180" />
                <el-table-column prop="packCode" label="编码" width="120" />
                <el-table-column prop="priority" label="优先级" width="100" />
                <el-table-column prop="enabled" label="启用" width="90">
                  <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="scope">
                    <el-button link type="primary" @click="toggleKbPack(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button>
                    <el-button link type="danger" @click="unbindPack(scope.row)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                class="pager"
                v-model:current-page="kbBindingPage"
                v-model:page-size="kbBindingPageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="filteredKbBindings.length"
              />
            </div>

            <div>
              <div class="table-title">知识库本地词条</div>
              <el-input v-model="localQuery" placeholder="搜索类别/术语" clearable style="width: 240px; margin-bottom: 8px" />
              <el-table :data="pagedLocalLexicons" border height="340">
                <el-table-column prop="categoryName" label="类别" width="140" />
                <el-table-column prop="term" label="术语" min-width="150" />
                <el-table-column prop="standardTerm" label="标准术语" min-width="150" />
                <el-table-column prop="enabled" label="启用" width="80">
                  <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="140">
                  <template #default="scope">
                    <el-button link type="primary" @click="openLocalDialog(scope.row)">编辑</el-button>
                    <el-button link type="danger" @click="removeLocal(scope.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                class="pager"
                v-model:current-page="localPage"
                v-model:page-size="localPageSize"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                :total="filteredLocalLexicons.length"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="packDialogVisible" :title="editingPackId ? '编辑词典包' : '新建词典包'" width="520px">
      <el-form :model="packForm" label-width="92px">
        <el-form-item label="编码"><el-input v-model="packForm.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="packForm.name" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="packForm.scopeType" style="width: 100%">
            <el-option label="GLOBAL" value="GLOBAL" />
            <el-option label="PRIVATE" value="PRIVATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="packForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="packForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePack">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="entryDialogVisible" :title="editingEntryId ? '编辑词条' : '新增词条'" width="560px">
      <el-form :model="entryForm" label-width="92px">
        <el-form-item label="类别">
          <el-select v-model="entryForm.categoryId" placeholder="选择类别" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="`${c.code} / ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="术语"><el-input v-model="entryForm.term" /></el-form-item>
        <el-form-item label="标准术语"><el-input v-model="entryForm.standardTerm" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="entryForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEntry">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryDialogVisible" :title="editingCategoryId ? '编辑类别' : '新增类别'" width="560px">
      <el-form :model="categoryForm" label-width="92px">
        <el-form-item label="编码"><el-input v-model="categoryForm.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="categoryForm.name" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="categoryForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortOrder" :min="1" :max="9999" style="width: 100%" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="categoryForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDialogVisible" :title="editingRelationId ? '编辑类别关系' : '新增类别关系'" width="560px">
      <el-form :model="relationForm" label-width="92px">
        <el-form-item label="源类别">
          <el-select v-model="relationForm.sourceCategoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="`r-s-${c.id}`" :label="`${c.code} / ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类别">
          <el-select v-model="relationForm.targetCategoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="`r-t-${c.id}`" :label="`${c.code} / ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系标签"><el-input v-model="relationForm.relationLabel" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="relationForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRelation">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="localDialogVisible" :title="editingLocalId ? '编辑本地词条' : '新增本地词条'" width="560px">
      <el-form :model="localForm" label-width="92px">
        <el-form-item label="类别">
          <el-select v-model="localForm.categoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="`local-${c.id}`" :label="`${c.code} / ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="术语"><el-input v-model="localForm.term" /></el-form-item>
        <el-form-item label="标准术语"><el-input v-model="localForm.standardTerm" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="localForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="localDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLocal">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '@/api'

const mainTab = ref('packs')

const packs = ref<any[]>([])
const packLoading = ref(false)
const selectedPack = ref<any>()
const entries = ref<any[]>([])
const entryLoading = ref(false)

const categories = ref<any[]>([])
const categoryRelations = ref<any[]>([])
const knowledgeBases = ref<any[]>([])
const knowledgeBaseId = ref<number>()
const kbPackBindings = ref<any[]>([])
const kbPackLoading = ref(false)
const localLexicons = ref<any[]>([])

const packQuery = ref('')
const entryQuery = ref('')
const categoryQuery = ref('')
const relationQuery = ref('')
const kbPackQuery = ref('')
const localQuery = ref('')
const categoryActiveOnly = ref(false)
const relationActiveOnly = ref(false)

const packPage = ref(1)
const packPageSize = ref(10)
const entryPage = ref(1)
const entryPageSize = ref(10)
const categoryPage = ref(1)
const categoryPageSize = ref(10)
const relationPage = ref(1)
const relationPageSize = ref(10)
const kbBindingPage = ref(1)
const kbBindingPageSize = ref(10)
const localPage = ref(1)
const localPageSize = ref(10)

const packToBind = ref<number>()
const bindPriority = ref(100)

const batch = reactive({ format: 'TEXT', content: '' })
const batchResult = ref<any>()

const packDialogVisible = ref(false)
const entryDialogVisible = ref(false)
const categoryDialogVisible = ref(false)
const relationDialogVisible = ref(false)
const localDialogVisible = ref(false)

const editingPackId = ref<number>()
const editingEntryId = ref<number>()
const editingCategoryId = ref<number>()
const editingRelationId = ref<number>()
const editingLocalId = ref<number>()

const packForm = reactive({ code: '', name: '', scopeType: 'GLOBAL', status: 'ACTIVE', description: '' })
const entryForm = reactive({ categoryId: undefined as number | undefined, term: '', standardTerm: '', enabled: true })
const categoryForm = reactive({ code: '', name: '', status: 'ACTIVE', sortOrder: 100, description: '' })
const relationForm = reactive({ sourceCategoryId: undefined as number | undefined, targetCategoryId: undefined as number | undefined, relationLabel: '', enabled: true })
const localForm = reactive({ categoryId: undefined as number | undefined, term: '', standardTerm: '', enabled: true })

const pageSlice = (arr: any[], page: number, pageSize: number) => arr.slice((page - 1) * pageSize, page * pageSize)
const findCategoryIdByCode = (code?: string) =>
  categories.value.find((c) => (c.code || '').toUpperCase() === (code || '').toUpperCase())?.id

const filteredPacks = computed(() => {
  const q = packQuery.value.trim().toLowerCase()
  if (!q) return packs.value
  return packs.value.filter((p) => `${p.name || ''} ${p.code || ''}`.toLowerCase().includes(q))
})
const pagedPacks = computed(() => pageSlice(filteredPacks.value, packPage.value, packPageSize.value))

const filteredEntries = computed(() => {
  const q = entryQuery.value.trim().toLowerCase()
  if (!q) return entries.value
  return entries.value.filter((e) => `${e.category || ''} ${e.term || ''} ${e.standardTerm || ''}`.toLowerCase().includes(q))
})
const pagedEntries = computed(() => pageSlice(filteredEntries.value, entryPage.value, entryPageSize.value))

const filteredCategories = computed(() => {
  const q = categoryQuery.value.trim().toLowerCase()
  if (!q) return categories.value
  return categories.value.filter((c) => `${c.code || ''} ${c.name || ''}`.toLowerCase().includes(q))
})
const pagedCategories = computed(() => pageSlice(filteredCategories.value, categoryPage.value, categoryPageSize.value))

const filteredRelations = computed(() => {
  const q = relationQuery.value.trim().toLowerCase()
  if (!q) return categoryRelations.value
  return categoryRelations.value.filter((r) => `${r.sourceCategoryName || ''} ${r.targetCategoryName || ''} ${r.sourceCategory || ''} ${r.targetCategory || ''} ${r.relationLabel || ''}`.toLowerCase().includes(q))
})
const pagedRelations = computed(() => pageSlice(filteredRelations.value, relationPage.value, relationPageSize.value))

const filteredKbBindings = computed(() => {
  const q = kbPackQuery.value.trim().toLowerCase()
  if (!q) return kbPackBindings.value
  return kbPackBindings.value.filter((r) => `${r.packName || ''} ${r.packCode || ''}`.toLowerCase().includes(q))
})
const pagedKbBindings = computed(() => pageSlice(filteredKbBindings.value, kbBindingPage.value, kbBindingPageSize.value))

const filteredLocalLexicons = computed(() => {
  const q = localQuery.value.trim().toLowerCase()
  if (!q) return localLexicons.value
  return localLexicons.value.filter((r) => `${r.category || ''} ${r.term || ''} ${r.standardTerm || ''}`.toLowerCase().includes(q))
})
const pagedLocalLexicons = computed(() => pageSlice(filteredLocalLexicons.value, localPage.value, localPageSize.value))

watch([packQuery, packPageSize], () => { packPage.value = 1 })
watch([entryQuery, entryPageSize], () => { entryPage.value = 1 })
watch([categoryQuery, categoryPageSize], () => { categoryPage.value = 1 })
watch([relationQuery, relationPageSize], () => { relationPage.value = 1 })
watch([kbPackQuery, kbBindingPageSize], () => { kbBindingPage.value = 1 })
watch([localQuery, localPageSize], () => { localPage.value = 1 })
watch(categoryActiveOnly, async () => {
  categoryPage.value = 1
  await loadCategories()
})
watch(relationActiveOnly, async () => {
  relationPage.value = 1
  await loadCategoryRelations()
})

const loadPacks = async () => {
  packLoading.value = true
  try {
    const { data } = await api.listDictionaryPacks()
    packs.value = data || []
    if (!selectedPack.value && packs.value.length) {
      selectedPack.value = packs.value[0]
      await loadEntries()
    }
  } finally {
    packLoading.value = false
  }
}

const loadEntries = async () => {
  if (!selectedPack.value) return
  entryLoading.value = true
  try {
    const { data } = await api.listDictionaryEntries(selectedPack.value.id)
    entries.value = data || []
  } finally {
    entryLoading.value = false
  }
}

const loadCategories = async () => {
  const { data } = await api.listDomainCategories({ activeOnly: categoryActiveOnly.value })
  categories.value = data || []
}

const loadCategoryRelations = async () => {
  const { data } = await api.listDomainCategoryRelations({ activeOnly: relationActiveOnly.value })
  categoryRelations.value = data || []
}

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data || []
  if (!knowledgeBaseId.value && knowledgeBases.value.length) {
    knowledgeBaseId.value = knowledgeBases.value[0].id
  }
}

const loadKbPackBindings = async () => {
  if (!knowledgeBaseId.value) return
  kbPackLoading.value = true
  try {
    const { data } = await api.listKnowledgeBaseDictionaryPacks(knowledgeBaseId.value)
    kbPackBindings.value = data || []
  } finally {
    kbPackLoading.value = false
  }
}

const loadLocalLexicons = async () => {
  if (!knowledgeBaseId.value) return
  const { data } = await api.listDomainLexicons(knowledgeBaseId.value)
  localLexicons.value = data || []
}

const onKbChanged = async () => {
  await Promise.all([loadKbPackBindings(), loadLocalLexicons()])
}

const onPackSelect = async (row: any) => {
  selectedPack.value = row
  await loadEntries()
}

const openPackDialog = (row?: any) => {
  if (row) {
    editingPackId.value = row.id
    packForm.code = row.code || ''
    packForm.name = row.name || ''
    packForm.scopeType = row.scopeType || 'GLOBAL'
    packForm.status = row.status || 'ACTIVE'
    packForm.description = row.description || ''
  } else {
    editingPackId.value = undefined
    packForm.code = ''
    packForm.name = ''
    packForm.scopeType = 'GLOBAL'
    packForm.status = 'ACTIVE'
    packForm.description = ''
  }
  packDialogVisible.value = true
}

const savePack = async () => {
  if (!packForm.code.trim() || !packForm.name.trim()) {
    ElMessage.warning('请填写词典包编码和名称')
    return
  }
  if (editingPackId.value) {
    await api.updateDictionaryPack(editingPackId.value, packForm)
  } else {
    await api.createDictionaryPack(packForm)
  }
  packDialogVisible.value = false
  ElMessage.success('词典包已保存')
  await loadPacks()
}

const removePack = async (row: any) => {
  await ElMessageBox.confirm(`确认删除词典包“${row.name}”？`, '提示', { type: 'warning' })
  await api.deleteDictionaryPack(row.id)
  ElMessage.success('词典包已删除')
  if (selectedPack.value?.id === row.id) {
    selectedPack.value = undefined
    entries.value = []
  }
  await loadPacks()
}

const openEntryDialog = (row?: any) => {
  if (!selectedPack.value) {
    ElMessage.warning('请先选择词典包')
    return
  }
  if (row) {
    editingEntryId.value = row.id
    entryForm.categoryId = row.categoryId || findCategoryIdByCode(row.category) || undefined
    entryForm.term = row.term || ''
    entryForm.standardTerm = row.standardTerm || ''
    entryForm.enabled = !!row.enabled
  } else {
    editingEntryId.value = undefined
    entryForm.categoryId = undefined
    entryForm.term = ''
    entryForm.standardTerm = ''
    entryForm.enabled = true
  }
  entryDialogVisible.value = true
}

const saveEntry = async () => {
  if (!selectedPack.value || !entryForm.categoryId || !entryForm.term.trim()) {
    ElMessage.warning('请填写类别和术语')
    return
  }
  if (editingEntryId.value) {
    await api.updateDictionaryEntry(editingEntryId.value, entryForm)
  } else {
    await api.upsertDictionaryEntry(selectedPack.value.id, entryForm)
  }
  entryDialogVisible.value = false
  ElMessage.success('词条已保存')
  await loadEntries()
}

const removeEntry = async (row: any) => {
  await ElMessageBox.confirm(`确认删除词条“${row.term}”？`, '提示', { type: 'warning' })
  await api.deleteDictionaryEntry(row.id)
  ElMessage.success('词条已删除')
  await loadEntries()
}

const submitBatch = async () => {
  if (!selectedPack.value || !batch.content.trim()) {
    ElMessage.warning('请先输入批量内容')
    return
  }
  const { data } = await api.batchUpsertDictionaryEntries(selectedPack.value.id, {
    format: batch.format,
    content: batch.content
  })
  batchResult.value = data
  ElMessage.success(`处理完成：成功${data.success}条`)
  await loadEntries()
}

const openCategoryDialog = (row?: any) => {
  if (row) {
    editingCategoryId.value = row.id
    categoryForm.code = row.code || ''
    categoryForm.name = row.name || ''
    categoryForm.status = row.status || 'ACTIVE'
    categoryForm.sortOrder = row.sortOrder ?? 100
    categoryForm.description = row.description || ''
  } else {
    editingCategoryId.value = undefined
    categoryForm.code = ''
    categoryForm.name = ''
    categoryForm.status = 'ACTIVE'
    categoryForm.sortOrder = 100
    categoryForm.description = ''
  }
  categoryDialogVisible.value = true
}

const saveCategory = async () => {
  if (!categoryForm.code.trim() || !categoryForm.name.trim()) {
    ElMessage.warning('请填写类别编码和名称')
    return
  }
  if (editingCategoryId.value) {
    await api.updateDomainCategory(editingCategoryId.value, categoryForm)
  } else {
    await api.createDomainCategory(categoryForm)
  }
  categoryDialogVisible.value = false
  ElMessage.success('类别已保存')
  await loadCategories()
}

const removeCategory = async (row: any) => {
  await ElMessageBox.confirm(`确认删除类别“${row.code}”？`, '提示', { type: 'warning' })
  await api.deleteDomainCategory(row.id)
  ElMessage.success('类别已删除')
  await loadCategories()
}

const openRelationDialog = (row?: any) => {
  if (row) {
    editingRelationId.value = row.id
    relationForm.sourceCategoryId = row.sourceCategoryId || findCategoryIdByCode(row.sourceCategory) || undefined
    relationForm.targetCategoryId = row.targetCategoryId || findCategoryIdByCode(row.targetCategory) || undefined
    relationForm.relationLabel = row.relationLabel || ''
    relationForm.enabled = !!row.enabled
  } else {
    editingRelationId.value = undefined
    relationForm.sourceCategoryId = undefined
    relationForm.targetCategoryId = undefined
    relationForm.relationLabel = ''
    relationForm.enabled = true
  }
  relationDialogVisible.value = true
}

const saveRelation = async () => {
  if (!relationForm.sourceCategoryId || !relationForm.targetCategoryId || !relationForm.relationLabel.trim()) {
    ElMessage.warning('请填写完整关联关系')
    return
  }
  if (editingRelationId.value) {
    await api.updateDomainCategoryRelation(editingRelationId.value, relationForm)
  } else {
    await api.createDomainCategoryRelation(relationForm)
  }
  relationDialogVisible.value = false
  ElMessage.success('关系已保存')
  await loadCategoryRelations()
}

const removeRelation = async (row: any) => {
  await ElMessageBox.confirm('确认删除该类别关系？', '提示', { type: 'warning' })
  await api.deleteDomainCategoryRelation(row.id)
  ElMessage.success('关系已删除')
  await loadCategoryRelations()
}

const bindPackToKb = async () => {
  if (!knowledgeBaseId.value || !packToBind.value) {
    ElMessage.warning('请选择知识库和词典包')
    return
  }
  await api.bindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, packToBind.value, {
    priority: bindPriority.value,
    enabled: true
  })
  ElMessage.success('已引入词典包')
  await loadKbPackBindings()
}

const toggleKbPack = async (row: any) => {
  if (!knowledgeBaseId.value) return
  await api.bindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, row.packId, {
    priority: row.priority,
    enabled: !row.enabled
  })
  ElMessage.success('状态已更新')
  await loadKbPackBindings()
}

const unbindPack = async (row: any) => {
  if (!knowledgeBaseId.value) return
  await api.unbindKnowledgeBaseDictionaryPack(knowledgeBaseId.value, row.packId)
  ElMessage.success('已移除词典包')
  await loadKbPackBindings()
}

const openLocalDialog = (row?: any) => {
  if (!knowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (row) {
    editingLocalId.value = row.id
    localForm.categoryId = row.categoryId || findCategoryIdByCode(row.category) || undefined
    localForm.term = row.term || ''
    localForm.standardTerm = row.standardTerm || ''
    localForm.enabled = !!row.enabled
  } else {
    editingLocalId.value = undefined
    localForm.categoryId = undefined
    localForm.term = ''
    localForm.standardTerm = ''
    localForm.enabled = true
  }
  localDialogVisible.value = true
}

const saveLocal = async () => {
  if (!knowledgeBaseId.value || !localForm.categoryId || !localForm.term.trim()) {
    ElMessage.warning('请填写本地词条信息')
    return
  }
  const payload = {
    knowledgeBaseId: knowledgeBaseId.value,
    categoryId: localForm.categoryId,
    term: localForm.term,
    standardTerm: localForm.standardTerm,
    enabled: localForm.enabled
  }
  if (editingLocalId.value) {
    await api.updateDomainLexicon(editingLocalId.value, payload)
  } else {
    await api.upsertDomainLexicon(payload)
  }
  localDialogVisible.value = false
  ElMessage.success('本地词条已保存')
  await loadLocalLexicons()
}

const removeLocal = async (row: any) => {
  await ElMessageBox.confirm(`确认删除词条“${row.term}”？`, '提示', { type: 'warning' })
  await api.deleteDomainLexicon(row.id)
  ElMessage.success('本地词条已删除')
  await loadLocalLexicons()
}

onMounted(async () => {
  await Promise.all([
    loadPacks(),
    loadKnowledgeBases(),
    loadCategories(),
    loadCategoryRelations()
  ])
  await onKbChanged()
})
</script>

<style scoped>
.lexicon-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  padding: 16px 20px;
}

.header-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2a3d;
}

.header-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.main-tabs {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 14px;
  padding: 14px;
}

.split-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.panel {
  padding: 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.toolbar.wrap {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #334155;
}

.table-title {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
}

.pager {
  margin-top: 10px;
  justify-content: flex-end;
}

.batch-collapse {
  margin-top: 12px;
}

.batch-result {
  color: #2563eb;
  font-size: 13px;
}

.empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1280px) {
  .split-2 {
    grid-template-columns: 1fr;
  }
}
</style>
