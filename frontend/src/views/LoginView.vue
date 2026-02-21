<template>
  <div class="login">
    <div class="login-card">
      <h2>AI文档协作平台</h2>
      <el-form :model="form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <div class="actions">
          <el-button type="primary" :loading="loading" @click="handleLogin">登录</el-button>
          <el-button :loading="registering" @click="handleRegister">注册</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const registering = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  try {
    loading.value = true
    const { data } = await api.login(form)
    auth.setAuth(data.token, data.username)
    router.push('/projects')
  } catch (err) {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!form.username.trim() || !form.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  try {
    registering.value = true
    const { data } = await api.register({
      username: form.username,
      password: form.password
    })
    auth.setAuth(data.token, data.username)
    ElMessage.success('注册成功，已按默认售前角色登录')
    router.push('/projects')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '注册失败')
  } finally {
    registering.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--page-bg);
}

.login-card {
  width: 360px;
  background: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.12);
}

.actions {
  display: flex;
  gap: 10px;
}
</style>
