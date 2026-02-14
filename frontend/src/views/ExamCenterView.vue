<template>
  <div class="page">
    <div class="card">
      <div class="header">
        <div class="header-title">考试中心（市场部）</div>
      </div>

      <div class="form-row">
        <el-select v-model="generateForm.knowledgeBaseId" placeholder="选择知识库" style="width: 240px">
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input v-model="generateForm.title" placeholder="试卷名称" style="width: 240px" />
        <el-button type="primary" @click="generateExam">AI生成试卷</el-button>
      </div>

      <el-table :data="papers" style="margin-top: 12px" @row-click="selectPaper">
        <el-table-column prop="id" label="试卷ID" width="100" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="totalScore" label="总分" width="100" />
        <el-table-column label="外链发布" width="170">
          <template #default="scope">
            <el-button size="small" @click.stop="publish(scope.row)">发布外链</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="scope">
            <el-button size="small" type="danger" @click.stop="removePaper(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card" style="margin-top: 16px" v-if="paperDetail">
      <div class="header">
        <div class="header-title">{{ paperDetail.title }}</div>
        <div>总分：{{ paperDetail.totalScore }}</div>
      </div>

      <div class="question" v-for="q in paperDetail.questions" :key="q.id">
        <div class="q-title">{{ q.sortIndex }}. {{ typeLabel(q.questionType) }}：{{ q.stem }} ({{ q.score }}分)</div>
        <div v-if="q.optionsJson" class="q-options">选项：{{ q.optionsJson }}</div>
        <el-input v-model="answers[q.id]" placeholder="请输入答案" />
      </div>

      <div class="submit-row">
        <el-button type="success" @click="submit">提交并AI判卷</el-button>
      </div>

      <div v-if="shareLink" class="result">
        <div>公开答题链接：</div>
        <el-input :model-value="shareLink" readonly />
      </div>

      <div v-if="submissionResult" class="result">
        <div>成绩：{{ submissionResult.score }}</div>
        <div style="white-space: pre-wrap">反馈：{{ submissionResult.aiFeedback }}</div>
      </div>

      <div v-if="leaderboard.length > 0" class="result">
        <div style="font-weight: 600; margin-bottom: 8px;">成绩榜</div>
        <el-table :data="leaderboard" size="small">
          <el-table-column prop="rank" label="排名" width="80" />
          <el-table-column prop="submitterName" label="答题人" width="140" />
          <el-table-column prop="score" label="分数" width="100" />
          <el-table-column prop="submittedAt" label="提交时间" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const knowledgeBases = ref<any[]>([])
const papers = ref<any[]>([])
const paperDetail = ref<any>(null)
const submissionResult = ref<any>(null)
const shareLink = ref('')
const leaderboard = ref<any[]>([])
const answers = reactive<Record<string, string>>({})

const generateForm = reactive({
  knowledgeBaseId: undefined as number | undefined,
  title: '市场部知识测评',
  instructions: '请根据知识库内容作答',
  singleChoiceCount: 5,
  judgeCount: 5,
  blankCount: 3,
  essayCount: 2
})

const loadKnowledgeBases = async () => {
  const { data } = await api.listKnowledgeBases()
  knowledgeBases.value = data
}

const loadPapers = async () => {
  if (!generateForm.knowledgeBaseId) return
  const { data } = await api.listExams(generateForm.knowledgeBaseId)
  papers.value = data
}

const generateExam = async () => {
  if (!generateForm.knowledgeBaseId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  await api.generateExam(generateForm)
  ElMessage.success('试卷生成完成')
  await loadPapers()
}

const selectPaper = async (row: any) => {
  const { data } = await api.getExam(row.id)
  paperDetail.value = data
  submissionResult.value = null
  Object.keys(answers).forEach((k) => delete answers[k])
  const board = await api.listExamSubmissions(row.id)
  leaderboard.value = board.data
}

const submit = async () => {
  if (!paperDetail.value) return
  const payload = { answersJson: JSON.stringify(answers) }
  const { data } = await api.submitExam(paperDetail.value.id, payload)
  submissionResult.value = data
  ElMessage.success('提交成功，已完成AI判卷')
  const board = await api.listExamSubmissions(paperDetail.value.id)
  leaderboard.value = board.data
}

const publish = async (row: any) => {
  const { data } = await api.publishExam(row.id)
  shareLink.value = `${window.location.origin}${data.sharePath}`
  ElMessage.success('外链已发布')
}

const removePaper = async (paperId: number) => {
  await api.deleteExam(paperId)
  ElMessage.success('试卷已删除')
  if (paperDetail.value?.id === paperId) {
    paperDetail.value = null
    submissionResult.value = null
    leaderboard.value = []
  }
  await loadPapers()
}

const typeLabel = (type: string) => {
  const map: Record<string, string> = {
    SINGLE: '单选题',
    JUDGE: '判断题',
    BLANK: '填空题',
    ESSAY: '简答题'
  }
  return map[type] || type
}

onMounted(loadKnowledgeBases)
watch(() => generateForm.knowledgeBaseId, () => {
  loadPapers()
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.form-row {
  display: flex;
  gap: 10px;
}

.question {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
}

.q-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.q-options {
  color: #64748b;
  margin-bottom: 8px;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
}

.result {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}
</style>
