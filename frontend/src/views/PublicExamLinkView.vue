<template>
  <div class="public-page">
    <div class="public-card" v-if="paper">
      <h2>{{ paper.title }}</h2>
      <p>{{ paper.instructions }}</p>
      <el-input v-model="submitterName" placeholder="请输入姓名" style="margin-bottom: 12px" />

      <div class="question" v-for="q in paper.questions" :key="q.id">
        <div class="q-title">{{ q.sortIndex }}. {{ typeLabel(q.questionType) }}：{{ q.stem }} ({{ q.score }}分)</div>
        <div v-if="q.optionsJson" class="q-options">选项：{{ q.optionsJson }}</div>
        <el-input v-model="answers[q.id]" placeholder="请输入答案" />
      </div>

      <el-button type="primary" @click="submit">提交并判卷</el-button>

      <div v-if="result" class="result">
        <div>成绩：{{ result.score }}</div>
        <div style="white-space: pre-wrap">反馈：{{ result.aiFeedback }}</div>
      </div>

      <div v-if="leaderboard.length > 0" class="result">
        <div style="font-weight: 600; margin-bottom: 8px;">当前成绩榜</div>
        <el-table :data="leaderboard" size="small">
          <el-table-column prop="rank" label="排名" width="80" />
          <el-table-column prop="submitterName" label="姓名" width="120" />
          <el-table-column prop="score" label="分数" width="100" />
          <el-table-column prop="submittedAt" label="提交时间" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const route = useRoute()
const paper = ref<any>(null)
const result = ref<any>(null)
const leaderboard = ref<any[]>([])
const submitterName = ref('')
const answers = reactive<Record<string, string>>({})

const load = async () => {
  const token = String(route.params.token)
  const { data } = await api.getPublicExam(token)
  paper.value = data
  const board = await api.listPublicLeaderboard(token)
  leaderboard.value = board.data
}

const submit = async () => {
  if (!submitterName.value.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  const token = String(route.params.token)
  const payload = {
    submitterName: submitterName.value,
    answersJson: JSON.stringify(answers)
  }
  const { data } = await api.submitPublicExam(token, payload)
  result.value = data
  const board = await api.listPublicLeaderboard(token)
  leaderboard.value = board.data
  ElMessage.success('提交成功')
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

onMounted(load)
</script>

<style scoped>
.public-page {
  min-height: 100vh;
  padding: 30px;
  background: linear-gradient(120deg, #f0f9ff, #eef2ff);
}

.public-card {
  max-width: 920px;
  margin: 0 auto;
  background: white;
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.1);
}

.question {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.q-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.q-options {
  color: #64748b;
  margin-bottom: 8px;
}

.result {
  margin-top: 16px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}
</style>
