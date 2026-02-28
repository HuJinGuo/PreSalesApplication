<template>
  <div class="workflow-list-view">
    <div class="page-header">
      <div class="header-content">
        <h2 class="page-title">编排任务中心</h2>
        <div class="page-desc">设计和管理基于大模型的流程编排，标准化“检索-生成-保存”自动化执行链路。</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" size="large" @click="openCreateWizard">
          <el-icon><Plus /></el-icon>新建编排任务
        </el-button>
      </div>
    </div>

    <div class="card panel mt-16">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query" placeholder="搜索编排名称" clearable style="width: 260px" />
          <el-select v-model="status" placeholder="全部状态" style="width: 140px">
            <el-option label="全部状态" value="" />
            <el-option label="运行中" value="running" />
            <el-option label="成功" value="success" />
            <el-option label="失败" value="error" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button @click="loadWorkflows">刷新</el-button>
        </div>
      </div>

      <el-table :data="pagedWorkflows" border stripe>
        <el-table-column prop="name" label="编排名称" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="240" />
        <el-table-column prop="lastRunStatus" label="最近运行状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.lastRunStatus)">{{ scope.row.lastRunStatus || '未运行' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="最近运行时间" width="160" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="runWorkflow(scope.row)">运行</el-button>
            <el-button size="small" type="primary" link @click="openDesign(scope.row)">编排设计</el-button>
            <el-button size="small" type="primary" link @click="viewLogs(scope.row)">执行日志</el-button>
            <el-button size="small" type="danger" link @click="deleteWorkflow(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="filteredWorkflows.length"
          layout="total, prev, pager, next, sizes"
          :page-sizes="[10, 20, 50]"
        />
      </div>
    </div>

    <!-- 双启动模式选择向导 -->
    <el-dialog v-model="createWizardVisible" title="选择编排启动模式" width="600px" top="15vh" class="create-wizard-dialog">
      <div class="wizard-cards">
        <div class="wizard-card" @click="createBlankWorkflow">
          <div class="card-icon"><el-icon><EditPen /></el-icon></div>
          <div class="card-content">
            <div class="card-title">无模板：自由画布编排</div>
            <div class="card-desc">从零开始拖拽节点，自由编排执行流程，适合高度定制化的数据拉取与处理。</div>
          </div>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>

        <div class="wizard-card template-card" @click="handleTemplateUpload">
          <div class="card-icon"><el-icon><Document /></el-icon></div>
          <div class="card-content">
            <div class="card-title">有模板：文档大纲驱动</div>
            <div class="card-desc">基于您上传的参考文档（Word/PDF），由 AI 自动解析其章节树结构并搭建核心流程架子。</div>
            <div class="upload-trigger" v-if="showUploadArea" @click.stop>
                <el-upload
                  action="#"
                  :auto-upload="false"
                  accept=".doc,.docx,.pdf"
                  :on-change="onTemplateChange"
                  drag
                >
                  <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                  <div class="el-upload__text">拖拽模板文件到此处，或 <em>点击上传</em></div>
                </el-upload>
            </div>
          </div>
          <el-icon class="arrow-icon" v-if="!showUploadArea"><ArrowRight /></el-icon>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, DocumentCopy, EditPen, Document, ArrowRight, UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()
const query = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(10)

const mockWorkflows = [
  { id: 1, name: '年度财务报告生成编排', description: '从本系统实施规范知识库抽取数据并生成结构化总结报告。', lastRunStatus: '成功', lastRunTime: '2026-02-28 10:30', createTime: '2026-01-15 14:00' },
  { id: 2, name: '企业竞对调查总结模板', description: '调用天眼查API与外部搜索引擎生成竞品分析文档。', lastRunStatus: '失败', lastRunTime: '2026-02-27 16:45', createTime: '2026-02-10 09:20' },
  { id: 3, name: '弱电工程智能标书生成', description: '基于项目概要、投标参数及文档示例生成标准的标书。', lastRunStatus: '运行中', lastRunTime: '2026-02-28 14:00', createTime: '2026-02-20 11:15' },
]

const workflows = ref(mockWorkflows)

const filteredWorkflows = computed(() => {
  let list = workflows.value
  if (query.value) {
    list = list.filter(w => w.name.includes(query.value) || w.description.includes(query.value))
  }
  if (status.value) {
    list = list.filter(w => {
      if (status.value === 'success') return w.lastRunStatus === '成功'
      if (status.value === 'error') return w.lastRunStatus === '失败'
      if (status.value === 'running') return w.lastRunStatus === '运行中'
      return true
    })
  }
  return list
})

const pagedWorkflows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredWorkflows.value.slice(start, start + pageSize.value)
})

const getStatusType = (status: string) => {
  if (status === '成功') return 'success'
  if (status === '失败') return 'danger'
  if (status === '运行中') return 'primary'
  return 'info'
}

const loadWorkflows = () => {
  // 静态页面模拟刷新
}

const createWizardVisible = ref(false)
const showUploadArea = ref(false)

const openCreateWizard = () => {
  showUploadArea.value = false
  createWizardVisible.value = true
}

const createBlankWorkflow = () => {
  createWizardVisible.value = false
  router.push(`/workflows/new/design`)
}

const handleTemplateUpload = () => {
  showUploadArea.value = true
}

const onTemplateChange = (file: any) => {
  console.log('检测到模板上传：', file.name)
  // 模拟上传成功后跳转到设计域 (并带有 templateId)
  createWizardVisible.value = false
  router.push(`/workflows/new/design?templateFile=${encodeURIComponent(file.name)}`)
}

const openDesign = (row: any) => {
  router.push(`/workflows/${row.id}/design`)
}

const runWorkflow = (row: any) => {
  // TODO: 后续显示全局参数填写弹窗
}

const viewLogs = (row: any) => {
  // TODO: 后续跳转到日志页面
}

const deleteWorkflow = (row: any) => {
  workflows.value = workflows.value.filter(w => w.id !== row.id)
}
</script>

<style scoped>
.workflow-list-view {
  padding: 2px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: white;
  padding: 20px 24px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.header-content h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #1f2f3d;
}
.page-desc {
  color: #5e6d82;
  font-size: 14px;
}
.mt-16 {
  margin-top: 16px;
}
.card.panel {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.toolbar-left, .toolbar-right {
  display: flex;
  gap: 12px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 启动向导样式 */
.wizard-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 10px 0 20px 0;
}
.wizard-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
}
.wizard-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.08);
  transform: translateY(-2px);
}
.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #eff6ff;
  color: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}
.template-card .card-icon {
  background: #f0fdf4;
  color: #22c55e;
}
.template-card:hover {
  border-color: #22c55e;
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.08);
}
.card-content {
  flex: 1;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6px;
}
.card-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.arrow-icon {
  font-size: 20px;
  color: #cbd5e1;
}
.wizard-card:hover .arrow-icon {
  color: #3b82f6;
}
.template-card:hover .arrow-icon {
  color: #22c55e;
}
.upload-trigger {
  margin-top: 16px;
}
:deep(.el-upload-dragger) {
  padding: 20px 10px;
}
</style>
