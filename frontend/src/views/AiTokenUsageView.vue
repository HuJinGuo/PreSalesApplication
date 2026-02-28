<template>
  <div class="token-page">
    <el-card class="filter-card" shadow="never">
      <div class="toolbar">
        <div class="title">AI Token 用量统计</div>
        <div class="actions">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            @change="loadData"
          />
          <el-button type="primary" @click="loadData">刷新</el-button>
        </div>
      </div>
    </el-card>

    <div class="summary-grid">
      <el-card shadow="never"><div class="label">总 Token</div><div class="value">{{ formatNum(summary.totalTokens) }}</div></el-card>
      <el-card shadow="never"><div class="label">输入 Token</div><div class="value">{{ formatNum(summary.totalPromptTokens) }}</div></el-card>
      <el-card shadow="never"><div class="label">输出 Token</div><div class="value">{{ formatNum(summary.totalCompletionTokens) }}</div></el-card>
      <el-card shadow="never"><div class="label">调用次数</div><div class="value">{{ formatNum(summary.totalRequests) }}</div></el-card>
      <el-card shadow="never"><div class="label">成功率</div><div class="value">{{ successRate }}%</div></el-card>
    </div>

    <div class="main-grid">
      <el-card shadow="never" class="main-card trend-card">
        <template #header>每日趋势</template>
        <div ref="trendRef" class="trend-chart"></div>
      </el-card>
      <div class="side-stack">
        <el-card shadow="never" class="main-card">
          <template #header>按服务商</template>
          <el-table :data="summary.providers" height="100%">
            <el-table-column prop="provider" label="服务商" />
            <el-table-column prop="totalTokens" label="总Token">
              <template #default="scope">{{ formatNum(scope.row.totalTokens) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never" class="main-card">
          <template #header>按模型</template>
          <el-table :data="summary.models" height="100%">
            <el-table-column prop="provider" label="服务商" width="90" />
            <el-table-column prop="modelName" label="模型" />
            <el-table-column prop="totalTokens" label="总Token" width="120">
              <template #default="scope">{{ formatNum(scope.row.totalTokens) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <el-card shadow="never" class="records-card">
      <template #header>调用明细</template>
      <el-table :data="records" :height="recordsTableHeight">
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="requestType" label="类型" width="130" />
        <el-table-column prop="provider" label="服务商" width="110" />
        <el-table-column prop="modelName" label="模型" min-width="180" />
        <el-table-column prop="scene" label="场景" width="160" />
        <el-table-column prop="totalTokens" label="总Token" width="110" />
        <el-table-column prop="latencyMs" label="耗时(ms)" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'" size="small">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="统计方式" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.estimated ? 'warning' : 'info'" size="small">
              {{ scope.row.estimated ? '估算' : '官方' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span :class="scope.row.success ? 'text-muted' : 'text-danger'">{{ scope.row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openRecordDetail(scope.row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next, sizes"
          :total="recordTotal"
          :current-page="recordPage"
          :page-size="recordSize"
          :page-sizes="[20, 50, 100]"
          @current-change="onRecordPageChange"
          @size-change="onRecordSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="showDetailDialog" title="调用详情" width="880px">
      <el-descriptions :column="2" border size="small" v-if="recordDetail">
        <el-descriptions-item label="TraceID">{{ recordDetail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ recordDetail.createdAt ? new Date(recordDetail.createdAt).toLocaleString() : '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ recordDetail.requestType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="场景">{{ recordDetail.scene || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务商 / 模型">{{ `${recordDetail.provider || '-'} / ${recordDetail.modelName || '-'}` }}</el-descriptions-item>
        <el-descriptions-item label="HTTP状态">{{ recordDetail.httpStatus ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Token(入/出/总)">{{ `${recordDetail.promptTokens || 0} / ${recordDetail.completionTokens || 0} / ${recordDetail.totalTokens || 0}` }}</el-descriptions-item>
        <el-descriptions-item label="耗时(ms)">{{ recordDetail.latencyMs || 0 }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="recordDetail.success ? 'success' : 'danger'" size="small">{{ recordDetail.success ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="统计方式">
          <el-tag :type="recordDetail.estimated ? 'warning' : 'info'" size="small">{{ recordDetail.estimated ? '估算' : '官方' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="知识库/文档/章节/任务">
          {{ `${recordDetail.knowledgeBaseId ?? '-'} / ${recordDetail.knowledgeDocumentId ?? '-'} / ${recordDetail.sectionId ?? '-'} / ${recordDetail.aiTaskId ?? '-'}` }}
        </el-descriptions-item>
        <el-descriptions-item label="错误码/请求ID">
          {{ `${recordDetail.errorCode || '-'} / ${recordDetail.vendorRequestId || '-'}` }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          <pre class="detail-pre">{{ recordDetail.errorMessage || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="请求摘要" :span="2">
          <pre class="detail-pre">{{ recordDetail.requestPayload || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应摘要" :span="2">
          <pre class="detail-pre">{{ recordDetail.responsePayload || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { api } from '@/api'

const today = new Date()
const start = new Date(today)
start.setDate(today.getDate() - 13)
const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

const dateRange = ref<[string, string]>([fmt(start), fmt(today)])
const summary = ref<any>({
  totalTokens: 0,
  totalPromptTokens: 0,
  totalCompletionTokens: 0,
  totalRequests: 0,
  successRequests: 0,
  daily: [],
  providers: [],
  models: []
})
const records = ref<any[]>([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordSize = ref(20)
const showDetailDialog = ref(false)
const recordDetail = ref<any>(null)
const trendRef = ref<HTMLElement | null>(null)
const recordsTableHeight = ref(260)
let trendChart: echarts.ECharts | null = null

const updateRecordsTableHeight = () => {
  const h = window.innerHeight
  recordsTableHeight.value = Math.max(220, Math.min(360, h - 650))
}

const loadData = async () => {
  const [startDate, endDate] = dateRange.value || []
  const [{ data }, { data: recordsResp }] = await Promise.all([
    api.getAiTokenUsageDaily({ startDate, endDate }),
    api.getAiTokenUsageRecords({ startDate, endDate, page: recordPage.value, size: recordSize.value })
  ])
  summary.value = data || summary.value
  records.value = (recordsResp?.records || []).map((row: any) => ({
    ...row,
    createdAt: row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'
  }))
  recordTotal.value = Number(recordsResp?.total || 0)
  await nextTick()
  renderTrendChart()
}

const formatNum = (n: number) => Number(n || 0).toLocaleString()

const successRate = computed(() => {
  const total = Number(summary.value.totalRequests || 0)
  const success = Number(summary.value.successRequests || 0)
  if (!total) return '0.00'
  return ((success / total) * 100).toFixed(2)
})

const calcRowRate = (row: any) => {
  const total = Number(row.requestCount || 0)
  const success = Number(row.successCount || 0)
  if (!total) return '0.00'
  return ((success / total) * 100).toFixed(2)
}

const onRecordPageChange = (page: number) => {
  recordPage.value = page
  loadData()
}

const onRecordSizeChange = (size: number) => {
  recordSize.value = size
  recordPage.value = 1
  loadData()
}

const openRecordDetail = async (row: any) => {
  const id = Number(row?.id || 0)
  if (!id) return
  const { data } = await api.getAiTokenUsageRecordDetail(id)
  recordDetail.value = data
  showDetailDialog.value = true
}

const renderTrendChart = () => {
  if (!trendRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendRef.value)
  }
  const daily = (summary.value?.daily || []) as any[]
  const x = daily.map(item => item.usageDate)
  const prompt = daily.map(item => Number(item.promptTokens || 0))
  const completion = daily.map(item => Number(item.completionTokens || 0))
  const total = daily.map(item => Number(item.totalTokens || 0))
  const requests = daily.map(item => Number(item.requestCount || 0))
  const rates = daily.map(item => Number(calcRowRate(item)))
  trendChart.setOption({
    grid: { left: 36, right: 46, top: 36, bottom: 28, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
    xAxis: { type: 'category', data: x, axisLabel: { color: '#64748b' } },
    yAxis: [
      { type: 'value', name: 'Token/调用', axisLabel: { color: '#64748b' } },
      { type: 'value', name: '成功率%', min: 0, max: 100, axisLabel: { color: '#64748b' } }
    ],
    series: [
      { name: '输入Token', type: 'line', smooth: true, data: prompt },
      { name: '输出Token', type: 'line', smooth: true, data: completion },
      { name: '总Token', type: 'line', smooth: true, data: total },
      { name: '调用数', type: 'line', smooth: true, data: requests },
      { name: '成功率', type: 'line', yAxisIndex: 1, smooth: true, data: rates }
    ]
  })
}

const handleResize = () => {
  updateRecordsTableHeight()
  trendChart?.resize()
}

onMounted(async () => {
  updateRecordsTableHeight()
  window.addEventListener('resize', handleResize)
  await loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  trendChart = null
})
</script>

<style scoped>
.token-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: calc(100vh - 106px);
  overflow: hidden;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  font-size: 18px;
  font-weight: 700;
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  flex: 0 0 auto;
}
.label {
  font-size: 12px;
  color: #64748b;
}
.value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}
.main-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 2.1fr) minmax(0, 1fr);
}
.main-card {
  height: 100%;
}
.trend-card {
  min-height: 0;
}
.trend-chart {
  height: 100%;
  min-height: 280px;
}
.side-stack {
  min-height: 0;
  display: grid;
  gap: 14px;
  grid-template-rows: 1fr 1fr;
}
.records-card {
  flex: 0 0 auto;
}
.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.detail-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow: auto;
}

.text-danger {
  color: #e35d6a;
}

.text-muted {
  color: #8da0ba;
}

:deep(.main-card > .el-card__body),
:deep(.trend-card > .el-card__body) {
  height: calc(100% - 58px);
  min-height: 0;
}

:deep(.main-card .el-table) {
  height: 100%;
}

@media (max-width: 1280px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .main-grid {
    grid-template-columns: 1fr;
  }
  .token-page {
    overflow: auto;
    height: auto;
  }
}
</style>
