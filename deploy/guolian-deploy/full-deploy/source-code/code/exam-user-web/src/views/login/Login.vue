<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="title">
        <el-icon :size="28" color="#1f2d3d"><Medal /></el-icon>
        <h2>纪检监察在线考试系统</h2>
        <p>用户端（考生 / 阅卷人员）</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="工号" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="onSubmit">登 录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await login(form.username.trim(), form.password)
    store.setLogin(data)
    if (data.mustChangePassword) {
      router.push('/change-password')
    } else {
      router.push(route.query.redirect || '/home')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2f4358 100%);
}
.login-card {
  width: 400px;
}
.title {
  text-align: center;
  margin-bottom: 24px;
}
.title h2 {
  margin: 8px 0 4px;
  font-size: 20px;
}
.title p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
</style>
