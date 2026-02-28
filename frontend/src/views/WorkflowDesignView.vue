<template>
  <div class="workflow-design-view">
    <div class="design-header">
      <div class="header-left">
        <el-button link @click="goBack" class="back-btn"><el-icon><ArrowLeft /></el-icon> 返回退出</el-button>
        <span class="workflow-title">{{ workflowName }}</span>
        <el-tag size="small" type="info" class="ml-2">草稿版</el-tag>
      </div>
      <div class="header-right">
        <el-button plain @click="testRun">调试运行</el-button>
        <el-button type="primary" @click="saveDraft">保存图表</el-button>
        <el-button type="success" @click="publish">发布版本</el-button>
      </div>
    </div>

    <div class="design-body">
      <!-- 左侧：文档大纲/节点池 (根据模式切换) -->
      <div class="left-panel">
        <el-tabs v-model="activeLeftTab" class="left-tabs">
          <!-- 文档解析模式下的特有 Tab -->
          <el-tab-pane label="文档解析大纲" name="outline" v-if="isTemplateMode">
            <div class="outline-container">
              <div class="outline-tip">选中下方文档中的任意片段，右键或点击插入节点，即可为该片段配置生成策略。</div>
              <el-tree
                :data="documentOutline"
                node-key="id"
                default-expand-all
                :expand-on-click-node="false"
                class="outline-tree"
              >
                <template #default="{ node, data }">
                  <span class="custom-tree-node">
                    <span>{{ node.label }}</span>
                    <span v-if="data.hasBoundNode" class="node-bound-tag">已绑节点</span>
                    <el-button v-else link type="primary" size="small" @click="insertNodeFromOutline(data)">
                      配置生成
                    </el-button>
                  </span>
                </template>
              </el-tree>
            </div>
          </el-tab-pane>

          <el-tab-pane label="标准算子" name="nodes">
            <div class="node-panel-content">
              <div class="node-group">
                <div class="group-title">基础调度</div>
                <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'start')">
                  <el-icon><VideoPlay /></el-icon> 全局输入起点
                </div>
                <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'end')">
                  <el-icon><DocumentChecked /></el-icon> 结果组装终点
                </div>
              </div>
              
              <div class="node-group">
                <div class="group-title">数据检索源</div>
                <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'tool-call')">
                  <el-icon><Key /></el-icon> 动态工具池调用
                </div>
                <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'kb-retrieve')">
                  <el-icon><Collection /></el-icon> 静态知识库挂载
                </div>
              </div>

              <div class="node-group">
                <div class="group-title">内容引擎</div>
                <div class="node-item" draggable="true" @dragstart="onDragStart($event, 'llm-generate')">
                  <el-icon><ChatDotRound /></el-icon> LLM 生成 / 分析
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 中心画布区 -->
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model="elements"
          :default-zoom="1.0"
          :min-zoom="0.2"
          :max-zoom="4"
          @pane-click="onPaneClick"
          @node-click="onNodeClick"
        >
          <Background pattern-color="#aaa" :gap="16" />
          <Controls />
        </VueFlow>
      </div>

      <!-- 右侧配置面板 -->
      <div class="config-panel" v-if="selectedNode">
        <div class="panel-title">配置面板 : {{ getSelectedNodeLabel() }}</div>
        <div class="config-form">
          <el-form label-position="top">
            <el-form-item label="节点显示名称">
              <el-input v-model="selectedNode.data.label" />
            </el-form-item>
            <el-form-item label="节点备注说明">
              <el-input v-model="selectedNode.data.desc" type="textarea" :rows="2" placeholder="填写该节点的用途..." />
            </el-form-item>
            
            <el-divider border-style="dashed" />

            <!-- 大模型特定配置 -->
            <template v-if="selectedNode.data.type === 'llm-generate'">
              <el-form-item label="指定推理模型">
                <el-select v-model="selectedNode.data.model" placeholder="请选择平台配置模型，默认优先 DeepSeek" style="width:100%">
                  <el-option label="默认配置模型" value="default" />
                  <el-option label="DeepSeek R1/Pro" value="deepseek" />
                  <el-option label="GPT-4o" value="gpt4" />
                </el-select>
              </el-form-item>
              <el-form-item label="System Prompt 模版与变量提取">
                <div class="help-text">使用 <code>{{'\{\{nodeId.output\}\}'}}</code> 引用上游节点传递的数据。</div>
                <el-input v-model="selectedNode.data.prompt" type="textarea" :rows="8" placeholder="例如：请作为资深标书写手，根据如下提供的大纲结构：\n {{'\{\{node_1.outline\}\}'}}，进行延展扩写..." />
              </el-form-item>
              <el-form-item label="高级参数">
                <div class="param-row">
                  <span>Temperature</span>
                  <el-slider v-model="selectedNode.data.temp" :step="0.1" :max="2" />
                </div>
              </el-form-item>
            </template>
            
            <!-- 知识库特定配置 -->
            <template v-if="selectedNode.data.type === 'kb-retrieve'">
              <el-form-item label="挂载数据源 (知识库选择)">
                <el-select v-model="selectedNode.data.kbId" placeholder="选择关联的一至多个知识库" style="width:100%" multiple>
                  <el-option label="企业内部通用语料库" value="1" />
                  <el-option label="往期招投标文件历史库" value="2" />
                  <el-option label="行业专业名词实施规范字典" value="3" />
                </el-select>
              </el-form-item>
              <el-form-item label="搜索 Query 前置组装策略">
                <div class="help-text">基于前序依赖注入的搜索关键词。</div>
                <el-input v-model="selectedNode.data.queryTemplate" type="textarea" :rows="3" placeholder="例如：查询关于 {{'\{\{start.project_name\}\}'}} 的历史施工方案。" />
              </el-form-item>
              <el-form-item label="Top-K">
                <el-input-number v-model="selectedNode.data.topk" :min="1" :max="20" />
              </el-form-item>
            </template>

            <!-- 动态工具池配置 -->
            <template v-if="selectedNode.data.type === 'tool-call'">
               <el-form-item label="配置可用工具挂载 (供LLM自行选择)">
                 <div class="help-text">选中这几个工具后，当前节点的 LLM 在执行时会根据您的 Prompt 自主判断调用哪个。</div>
                  <el-select v-model="selectedNode.data.tools" placeholder="选择要注入该节点的工具" style="width:100%" multiple>
                    <el-option label="天眼查企业关系穿透插件" value="tianyancha" />
                    <el-option label="内部项目标段金额计算器" value="calculator" />
                    <el-option label="行业气象水文数据拉取" value="weather" />
                    <el-option label="本节点上下文搜索引擎" value="search" />
                  </el-select>
               </el-form-item>
               <el-form-item label="引导指令 (System Prompt)">
                 <el-input v-model="selectedNode.data.toolPrompt" type="textarea" :rows="4" placeholder="例如：请查询关于本标段所在地区的近一个月降水情况，并返回成文本形式提供下个节点使用。" />
               </el-form-item>
            </template>
            
          </el-form>
        </div>
      </div>
      <div class="config-panel empty-config" v-else>
        <el-icon size="40" color="#cbd5e1"><Setting /></el-icon>
        <div class="mt-4">在左侧拖拽算子到画布设计流程图<br/>随后点击图中节点，将在此配置详细处理策略</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, VideoPlay, DocumentChecked, Collection, Key, ChatDotRound, Cpu, Switch, Setting } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const router = useRouter()
const route = useRoute()

const isTemplateMode = computed(() => !!route.query.templateFile)
const workflowName = ref(route.query.templateFile ? `解析模板: ${route.query.templateFile}` : '新建空白流程编排')
const activeLeftTab = ref(isTemplateMode.value ? 'outline' : 'nodes')

// 模拟文档树
const documentOutline = ref([
  {
    id: 'ch1',
    label: '1. 建设项目概况',
    hasBoundNode: false,
    children: [
      { id: 'ch1-1', label: '1.1 项目背景', hasBoundNode: true },
      { id: 'ch1-2', label: '1.2 地理位置与环境情况', hasBoundNode: false }
    ]
  },
  {
    id: 'ch2',
    label: '2. 竞争对手分析',
    hasBoundNode: false
  }
])

const { addNodes, addEdges, onConnect, dimensions, screenToFlowCoordinate } = useVueFlow()

// Mock DAG Data
const elements = ref([
  { id: 'start', type: 'input', label: '全局输入: 用户交互起点', position: { x: 50, y: 300 }, data: { label: '解析参数传入', type: 'start', desc: '该节点将捕获用户全局填写的参数' } },
  { id: 'llm_1', type: 'default', label: '1.1 项目背景 (已挂载节点)', position: { x: 350, y: 200 }, data: { label: '撰写 1.1 项目背景', type: 'llm-generate', prompt: '结合所选项目信息生成本章背景...' } }
])

const selectedNodeId = ref<string|null>(null)
const selectedNode = ref<any>(null)

let idCounter = 1

onConnect((params) => {
  addEdges([params])
})

const onPaneClick = () => {
  selectedNodeId.value = null
  selectedNode.value = null
}

const onNodeClick = (event: any) => {
  const node = elements.value.find(e => e.id === event.node.id)
  if (node) {
    selectedNodeId.value = node.id
    selectedNode.value = node
  }
}

const getSelectedNodeLabel = () => {
  return selectedNode.value?.data?.label || '未命名节点'
}

const onDragStart = (event: DragEvent, nodeType: string) => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', nodeType)
    event.dataTransfer.effectAllowed = 'move'
  }
}

const onDrop = (event: DragEvent) => {
  const type = event.dataTransfer?.getData('application/vueflow')
  if (!type) return

  const position = screenToFlowCoordinate({
    x: event.clientX,
    y: event.clientY,
  })

  const newNodeId = `node_${Date.now()}_${idCounter++}`
  
  let label = '新建节点'
  if (type === 'start') label = '输入起点'
  if (type === 'end') label = '输出产物'
  if (type === 'kb-retrieve') label = '知识库挂载'
  if (type === 'tool-call') label = '工具池调用'
  if (type === 'llm-generate') label = '大模型生成'

  const newNode = {
    id: newNodeId,
    type: (type === 'start' ? 'input' : (type === 'end' ? 'output' : 'default')),
    position,
    label,
    data: { label, type, desc: '' }
  }

  elements.value.push(newNode as any)
}

const insertNodeFromOutline = (data: any) => {
  const newNodeId = `node_${Date.now()}`
  const newNode = {
    id: newNodeId,
    type: 'default',
    position: { x: 300, y: 300 },
    label: `生成 [${data.label}]`,
    data: { 
      label: `片段生成: ${data.label}`, 
      type: 'llm-generate', 
      prompt: `请针对章节【${data.label}】结合以下条件进行生成：...` 
    }
  }
  elements.value.push(newNode as any)
  data.hasBoundNode = true
  // 自动选中新节点
  setTimeout(() => {
    selectedNodeId.value = newNodeId
    selectedNode.value = newNode
  }, 100)
}

onMounted(() => {
  // force vue flow re-measure layout
  setTimeout(() => {
     const flowPane = document.querySelector('.vue-flow__pane')
     if(flowPane) flowPane.dispatchEvent(new Event('resize'))
  }, 100)
})

const goBack = () => router.push('/workflows')
const saveDraft = () => {}
const testRun = () => {}
const publish = () => {}

</script>

<style scoped>
.workflow-design-view {
  height: calc(100vh - 61px);
  display: flex;
  flex-direction: column;
  background: #f8f9fc;
  margin: -18px; /* Override main layout padding */
}
.design-header {
  height: 54px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  font-size: 14px;
  color: #4a5568;
}
.workflow-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a202c;
  padding-left: 14px;
  border-left: 1px solid #e2e8f0;
}
.header-right {
  display: flex;
  gap: 10px;
}
.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.left-panel {
  width: 280px;
  background: white;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}
.left-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}
:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
.outline-container {
  padding: 16px;
}
.outline-tip {
  font-size: 12px;
  color: #718096;
  background: #f7fafc;
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 16px;
  line-height: 1.5;
}
.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding-right: 8px;
}
.node-bound-tag {
  font-size: 11px;
  color: #38a169;
  background: #f0fff4;
  padding: 2px 6px;
  border-radius: 4px;
}
.node-panel-content {
  padding: 20px 16px;
}
.node-group {
  margin-bottom: 24px;
}
.group-title {
  font-size: 12px;
  color: #a0aec0;
  margin-bottom: 8px;
  font-weight: 600;
  text-transform: uppercase;
}
.node-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #f7fafc;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: grab;
  font-size: 13px;
  font-weight: 500;
  color: #2d3748;
  transition: all 0.2s;
}
.node-item:hover {
  background: #ebf8ff;
  border-color: #bee3f8;
  color: #2b6cb0;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0,0,0,0.03);
}
.canvas-area {
  flex: 1;
  position: relative;
}
.config-panel {
  width: 360px;
  background: white;
  border-left: 1px solid #e2e8f0;
  padding: 20px 18px;
  overflow-y: auto;
}
.empty-config {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #a0aec0;
  font-size: 13px;
  text-align: center;
  padding: 40px;
  white-space: pre-line;
}
.mt-4 { margin-top: 16px; line-height: 1.6; }
.ml-2 { margin-left: 8px; }

.config-form {
  margin-top: 8px;
}
.help-text {
  font-size: 12px;
  color: #718096;
  margin-bottom: 8px;
  line-height: 1.4;
}
.help-text code {
  color: #e53e3e;
  background: #fff5f5;
  padding: 2px 4px;
  border-radius: 4px;
}
.param-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.param-row span {
  width: 80px;
  font-size: 13px;
  color: #4a5568;
}

:deep(.vue-flow__node-input) {
  border-radius: 8px;
  border: 2px solid #68d391;
  background: #f0fff4;
  color: #2f855a;
  font-weight: 600;
}

:deep(.vue-flow__node-output) {
  border-radius: 8px;
  border: 2px solid #fc8181;
  background: #fff5f5;
  color: #c53030;
  font-weight: 600;
}

:deep(.vue-flow__node-default) {
  border-radius: 8px;
  border: 2px solid #63b3ed;
  background: #ffffff;
  padding: 12px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  font-weight: 600;
  color: #2a4365;
}
</style>
