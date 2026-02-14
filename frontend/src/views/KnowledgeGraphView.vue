<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">知识图谱与检索</div>
      </div>

      <div class="search-row">
        <el-select v-model="knowledgeBaseId" placeholder="选择知识库" style="width: 240px" @change="onKbChange">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input v-model="searchQuery" placeholder="输入检索内容" style="width: 360px" />
        <el-input-number v-model="minScore" :min="0" :max="1" :step="0.05" />
        <el-switch v-model="rerank" active-text="AI重排" />
        <el-button type="primary" @click="search">知识检索</el-button>
      </div>

      <el-table :data="searchResults" style="margin-top: 12px">
        <el-table-column prop="chunkId" label="ChunkID" width="100" />
        <el-table-column prop="score" label="相似度" width="120" />
        <el-table-column prop="content" label="内容片段" />
      </el-table>

      <div class="header" style="margin-top: 16px;">
        <div class="header-title">知识图谱</div>
        <el-button @click="loadGraph">刷新图谱</el-button>
      </div>
      <div ref="graphRef" class="graph-panel"></div>
      <div class="graph-tip">可点击节点查看详情</div>
    </div>

    <el-drawer v-model="showNodeDetail" size="42%" :title="nodeDetail?.name || '节点详情'">
      <div v-if="nodeDetail" class="node-detail">
        <div class="node-meta">
          <el-tag size="small">{{ nodeDetail.nodeType }}</el-tag>
          <span>文档数：{{ (nodeDetail.relatedDocuments || []).length }}</span>
          <span>片段数：{{ (nodeDetail.relatedChunks || []).length }}</span>
        </div>
        <div class="node-summary">{{ nodeDetail.summary }}</div>
        <div class="node-section">
          <div class="node-section-title">关联文档</div>
          <el-table :data="nodeDetail.relatedDocuments || []" size="small">
            <el-table-column prop="documentId" label="文档ID" width="90" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="hitCount" label="命中次数" width="100" />
          </el-table>
        </div>
        <div class="node-section" v-if="nodeDetail.relatedKeywords?.length">
          <div class="node-section-title">关联关键词</div>
          <div class="keywords">
            <el-tag v-for="kw in nodeDetail.relatedKeywords" :key="kw" size="small">{{ kw }}</el-tag>
          </div>
        </div>
        <div class="node-section">
          <div class="node-section-title">相关片段</div>
          <el-table :data="nodeDetail.relatedChunks || []" size="small">
            <el-table-column prop="documentTitle" label="文档" width="220" />
            <el-table-column prop="chunkIndex" label="段" width="70" />
            <el-table-column prop="hitCount" label="命中" width="70" />
            <el-table-column prop="snippet" label="片段预览" />
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { api } from '@/api'

const knowledgeBases = ref<any[]>([])
const knowledgeBaseId = ref<number>()
const searchQuery = ref('')
const minScore = ref(0.1)
const rerank = ref(true)
const searchResults = ref<any[]>([])
const graphRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
const showNodeDetail = ref(false)
const nodeDetail = ref<any>(null)

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data
  if (!knowledgeBaseId.value && data.length > 0) {
    knowledgeBaseId.value = data[0].id
    await nextTick()
    await loadGraph()
  }
}

const onKbChange = async () => {
  searchResults.value = []
  await loadGraph()
}

const search = async () => {
  if (!knowledgeBaseId.value || !searchQuery.value.trim()) return
  const { data } = await api.searchKnowledge(knowledgeBaseId.value, {
    query: searchQuery.value,
    topK: 8,
    candidateTopK: 30,
    minScore: minScore.value,
    rerank: rerank.value
  })
  searchResults.value = data
}

const loadGraph = async () => {
  if (!knowledgeBaseId.value || !graphRef.value) return
  const { data } = await api.getKnowledgeGraph(knowledgeBaseId.value)
  if (!chart) chart = echarts.init(graphRef.value)
  chart.off('click')
  chart.setOption({
    tooltip: {},
    legend: [{ data: ['DOCUMENT', 'DOMAIN_ENTITY', 'KEYWORD'] }],
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        symbolSize: (val: number) => Math.max(10, Math.min(48, Number(val) || 12)),
        force: { repulsion: 260, edgeLength: [50, 160] },
        categories: [{ name: 'DOCUMENT' }, { name: 'DOMAIN_ENTITY' }, { name: 'KEYWORD' }],
        label: { show: true, position: 'right', fontSize: 11 },
        lineStyle: { color: 'source', curveness: 0.08 },
        data: (data.nodes || []).map((n: any) => ({
          id: n.id,
          name: n.name,
          value: n.value,
          category: n.category === 'DOCUMENT' ? 0 : (n.category === 'DOMAIN_ENTITY' ? 1 : 2)
        })),
        links: (data.edges || []).map((e: any) => ({
          source: e.source,
          target: e.target,
          value: e.value,
          label: { show: true, formatter: e.label, fontSize: 10, color: '#7a8799' }
        }))
      }
    ]
  })
  chart.on('click', (params: any) => {
    if (params.dataType !== 'node' || !knowledgeBaseId.value) return
    const nodeId = params.data?.id
    if (!nodeId) return
    api.getKnowledgeGraphNodeDetail(knowledgeBaseId.value, nodeId).then(({ data }) => {
      nodeDetail.value = data
      showNodeDetail.value = true
    })
  })
}

const onResize = () => chart?.resize()
onMounted(loadKnowledgeBases)
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.graph-panel {
  margin-top: 10px;
  height: 460px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fafc, #eef2ff);
}

.graph-tip {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.node-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.node-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.node-summary {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
}

.node-section-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.keywords {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
