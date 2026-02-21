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
        <div style="display: flex; gap: 12px; align-items: center">
          <el-radio-group v-model="layoutType" size="small" @change="reloadChart">
            <el-radio-button label="force">力导向</el-radio-button>
            <el-radio-button label="circular">环形</el-radio-button>
          </el-radio-group>
          <el-button @click="loadGraph" size="small" icon="Refresh">整图刷新</el-button>
        </div>
      </div>
      <div ref="graphRef" class="graph-panel"></div>
      <div class="graph-tip">可拖拽节点调整位置，点击节点查看详情，双击空白处复位</div>

      <div class="header" style="margin-top: 16px;">
        <div class="header-title">向量数据列表</div>
        <el-button size="small" @click="loadChunks">刷新列表</el-button>
      </div>
      <el-table :data="pagedChunks" style="margin-top: 10px">
        <el-table-column prop="id" label="ChunkID" width="100" />
        <el-table-column label="文档名称" min-width="180">
          <template #default="scope">
            {{ documentNameMap[scope.row.knowledgeDocumentId] || `#${scope.row.knowledgeDocumentId}` }}
          </template>
        </el-table-column>
        <el-table-column prop="chunkIndex" label="段序" width="90" />
        <el-table-column prop="embeddingDim" label="向量维度" width="100" />
        <el-table-column label="内容片段" min-width="360">
          <template #default="scope">
            <div class="chunk-cell">
              <div :class="expandedChunkIds.has(scope.row.id) ? 'chunk-text expanded' : 'chunk-text collapsed'">
                {{ scope.row.content }}
              </div>
              <el-button
                link
                type="primary"
                size="small"
                @click="toggleChunkExpand(scope.row.id)"
              >
                {{ expandedChunkIds.has(scope.row.id) ? '收起' : '展开' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" @click="openVectorDialog(scope.row)">查看向量</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="chunkPage"
          v-model:page-size="chunkPageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="chunkRows.length"
          :page-sizes="[10, 20, 50, 100]"
        />
      </div>
    </div>

    <el-drawer v-model="showNodeDetail" size="46%" :with-header="false" class="node-drawer">
      <div v-if="nodeDetail" class="node-detail">
        <div class="node-hero">
          <div class="node-hero-top">
            <div class="node-hero-title">{{ nodeDetail.name || '节点详情' }}</div>
            <el-button text @click="showNodeDetail = false">关闭</el-button>
          </div>
          <div class="node-meta">
            <el-tag size="small" type="primary">{{ nodeDetail.nodeType }}</el-tag>
            <span>文档数：{{ (nodeDetail.relatedDocuments || []).length }}</span>
            <span>片段数：{{ (nodeDetail.relatedChunks || []).length }}</span>
          </div>
        </div>
        <div class="node-summary card-soft">{{ nodeDetail.summary }}</div>
        <div class="node-section card-soft">
          <div class="node-section-title">关联文档</div>
          <el-table :data="nodeDetail.relatedDocuments || []" size="small" max-height="220">
            <el-table-column prop="documentId" label="文档ID" width="90" />
            <el-table-column prop="title" label="标题" />
            <el-table-column prop="hitCount" label="命中次数" width="100" />
          </el-table>
        </div>
        <div class="node-section card-soft" v-if="nodeDetail.relatedKeywords?.length">
          <div class="node-section-title">关联关键词</div>
          <div class="keywords">
            <el-tag v-for="kw in nodeDetail.relatedKeywords" :key="kw" size="small" effect="plain">{{ kw }}</el-tag>
          </div>
        </div>
        <div class="node-section card-soft related-chunks-section">
          <div class="node-section-title">相关片段</div>
          <el-table :data="nodeDetail.relatedChunks || []" size="small" max-height="260">
            <el-table-column prop="documentTitle" label="文档" width="220" />
            <el-table-column prop="chunkIndex" label="段" width="70" />
            <el-table-column prop="hitCount" label="命中" width="70" />
            <el-table-column prop="snippet" label="片段预览" />
          </el-table>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="showVectorDialog" width="860px" title="向量详情" class="vector-dialog" top="6vh">
      <div class="vector-dialog-body">
        <div class="vector-hero">
          <div class="vector-title">{{ currentChunkDocumentName }}</div>
          <div class="vector-meta">
            <el-tag size="small" type="primary">Chunk #{{ currentChunk?.id }}</el-tag>
            <el-tag size="small">段序 {{ currentChunk?.chunkIndex }}</el-tag>
            <el-tag size="small" type="success">向量维度 {{ currentChunk?.embeddingDim }}</el-tag>
            <el-tag size="small" type="info">文档ID {{ currentChunk?.knowledgeDocumentId }}</el-tag>
          </div>
        </div>
        <div class="vector-content-card">
          <div class="node-section-title">内容片段</div>
          <pre class="vector-box">{{ currentChunk?.content || '暂无内容' }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
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
const layoutType = ref('force')
const graphData = ref<any>({ nodes: [], edges: [] })
const chunkRows = ref<any[]>([])
const documentNameMap = ref<Record<number, string>>({})
const chunkPage = ref(1)
const chunkPageSize = ref(10)
const showVectorDialog = ref(false)
const currentChunk = ref<any>(null)
const expandedChunkIds = ref<Set<number>>(new Set())
const currentChunkDocumentName = computed(() => {
  const docId = currentChunk.value?.knowledgeDocumentId
  if (!docId) return '未知文档'
  return documentNameMap.value[docId] || `文档 #${docId}`
})
const pagedChunks = computed(() => {
  const start = (chunkPage.value - 1) * chunkPageSize.value
  return chunkRows.value.slice(start, start + chunkPageSize.value)
})

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data
  if (!knowledgeBaseId.value && data.length > 0) {
    knowledgeBaseId.value = data[0].id
    await nextTick()
    await loadGraph()
    await loadChunks()
  }
}

const onKbChange = async () => {
  searchResults.value = []
  await loadGraph()
  await loadChunks()
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

const reloadChart = () => {
  if (!chart || !graphData.value.nodes.length) return
  const option = chart.getOption() as any
  
  // 保持当前的 nodes 和 links
  const series = option.series[0]
  series.layout = layoutType.value
  
  if (layoutType.value === 'circular') {
    series.force = null;
  } else {
    series.force = {
      repulsion: 800,    // 增大斥力，把节点推开
      gravity: 0.05,     // 减小引力，防止聚成一团
      edgeLength: [80, 250], // 拉长连线
      layoutAnimation: true
    }
  }
  
  chart.setOption({ series: [series] })
}

const loadGraph = async () => {
  if (!knowledgeBaseId.value || !graphRef.value) return
  const { data } = await api.getKnowledgeGraph(knowledgeBaseId.value)
  graphData.value = data
  
  if (!chart) chart = echarts.init(graphRef.value)
  chart.off('click')
  chart.resize() // Ensure size is correct

  // 预处理节点样式：文档节点更大，关键词更小但紧凑
  const nodes = (data.nodes || []).map((n: any) => {
    let size = Math.max(15, Math.min(50, Number(n.value) || 15));
    let color = '#f59e0b'; // Default Keyword: Amber
    
    if (n.category === 'DOCUMENT') {
      size = 55; // 文档固定大节点
      color = '#3b82f6'; // Blue
    } else if (n.category === 'DOMAIN_ENTITY') {
      size = Math.max(30, size);
      color = '#10b981'; // Green
    }
    
    return {
      id: n.id,
      name: n.name,
      value: n.value,
      symbolSize: size,
      itemStyle: { color: color },
      category: n.category === 'DOCUMENT' ? 0 : (n.category === 'DOMAIN_ENTITY' ? 1 : 2),
      // 启用拖拽
      draggable: true
    }
  })

  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: [{ 
      data: [
        { name: 'DOCUMENT', icon: 'circle', textStyle: { color: '#3b82f6' } },
        { name: 'DOMAIN_ENTITY', icon: 'circle', textStyle: { color: '#10b981' } }, 
        { name: 'KEYWORD', icon: 'circle', textStyle: { color: '#f59e0b' } }
      ],
      selectedMode: true 
    }],
    series: [
      {
        type: 'graph',
        layout: layoutType.value,
        roam: true, // 允许缩放和平移
        draggable: true,
        force: {
          repulsion: 1000,
          gravity: 0.06,
          edgeLength: [100, 300],
          friction: 0.6
        },
        categories: [
          { name: 'DOCUMENT' }, 
          { name: 'DOMAIN_ENTITY' }, 
          { name: 'KEYWORD' }
        ],
        label: {
          show: true,
          position: 'right',
          fontSize: 12,
          color: '#334155',
          formatter: (p: any) => {
             return p.name.length > 10 ? p.name.substring(0, 10) + '...' : p.name
          }
        },
        lineStyle: {
          color: '#cbd5e1',
          curveness: 0.1,
          width: 1.5,
          opacity: 0.6
        },
        emphasis: {
          focus: 'adjacency', // 高亮相邻节点
          lineStyle: { width: 3 }
        },
        data: nodes,
        links: (data.edges || []).map((e: any) => ({
          source: e.source,
          target: e.target,
          value: e.value,
          label: { show: false } // 平时不显示连线文字，太乱
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

const loadChunks = async () => {
  if (!knowledgeBaseId.value) return
  const [chunksRes, docsRes] = await Promise.all([
    api.listKnowledgeChunks(knowledgeBaseId.value),
    api.listKnowledgeDocuments(knowledgeBaseId.value)
  ])
  const data = chunksRes.data || []
  chunkRows.value = data || []
  const map: Record<number, string> = {}
  for (const doc of docsRes.data || []) {
    map[doc.id] = doc.title || doc.fileName || `#${doc.id}`
  }
  documentNameMap.value = map
  expandedChunkIds.value = new Set()
  chunkPage.value = 1
}

const openVectorDialog = (row: any) => {
  currentChunk.value = row
  showVectorDialog.value = true
}

const toggleChunkExpand = (chunkId: number) => {
  const set = new Set(expandedChunkIds.value)
  if (set.has(chunkId)) {
    set.delete(chunkId)
  } else {
    set.add(chunkId)
  }
  expandedChunkIds.value = set
}

const onResize = () => chart?.resize()
onMounted(loadKnowledgeBases)
onMounted(loadChunks)
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

.node-hero {
  border: 1px solid #dce6f6;
  border-radius: 12px;
  background: linear-gradient(180deg, #f7fbff, #f1f6ff);
  padding: 12px;
}

.node-hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.node-hero-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2a3d;
  line-height: 1.2;
}

.node-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
  flex-wrap: wrap;
}

.node-summary {
  color: #334155;
  line-height: 1.65;
}

.node-section-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.card-soft {
  border: 1px solid #e4ebf5;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.related-chunks-section {
  margin-top: 10px;
}

.keywords {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  color: #334155;
}

.chunk-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chunk-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.45;
}

.chunk-text.collapsed {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.vector-box {
  margin: 6px 0 0;
  max-height: 52vh;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
}

.vector-dialog-body {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.vector-hero {
  border: 1px solid #dbe5f1;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff, #f3f7fd);
  padding: 12px;
}

.vector-title {
  font-weight: 700;
  color: #1f2a3d;
  margin-bottom: 8px;
}

.vector-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.vector-content-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

:deep(.node-drawer .el-drawer__body) {
  overflow-y: auto;
  padding: 14px 14px 16px;
  background: linear-gradient(180deg, #f8fbff, #f3f6fb);
}

:deep(.node-drawer .el-table) {
  border-radius: 10px;
  overflow: hidden;
}
</style>
