<template>
  <div class="login-page">
    <div class="login-box" v-if="ready" v-loading="ssoLoading" element-loading-text="单点登录中...">
      <h2>“纪检监察业务”学习平台</h2>
      <p class="sub">管理端登录</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="工号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
    </div>
    <div v-else class="sso-wait">登录成功，正在进入…</div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../../store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)
const ssoLoading = ref(false)
const ready = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 门户统一入口 /login 已完成 ticket 换令牌并写入本地存储，这里直接跳首页（不渲染登录表单，避免闪烁）
onMounted(async () => {
  if (sessionStorage.getItem('sso_redirect')) {
    sessionStorage.removeItem('sso_redirect')
    router.replace(route.query.redirect || '/')
    return
  }
  ready.value = true
  // 国联 APP/门户单点免密登录：地址携带 ?ticket=xxx 跳转回来时自动登录
  const ticket = route.query.ticket
  if (!ticket || typeof ticket !== 'string') return
  ssoLoading.value = true
  try {
    const data = await auth.loginByTicket(ticket.trim())
    ElMessage.success('单点登录成功')
    if (data.mustChangePassword) {
      router.replace('/change-password')
    } else {
      router.replace(route.query.redirect || '/')
    }
  } catch (e) {
    // 拦截器已提示具体原因，回退到工号密码登录
  } finally {
    ssoLoading.value = false
  }
})

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    if (data.mustChangePassword) {
      router.push('/change-password')
    } else {
      router.push(route.query.redirect || '/')
    }
  } catch (e) {
    // 拦截器已提示
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
  background: linear-gradient(135deg, #1f3a5f 0%, #2d5a8a 100%);
}
.login-box {
  width: 380px;
  background: #fff;
  border-radius: 8px;
  padding: 40px 36px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}
h2 {
  margin: 0;
  text-align: center;
  font-size: 20px;
  color: #303133;
}
.sso-wait {
  color: #fff;
  font-size: 16px;
  letter-spacing: 1px;
}
.sub {
  text-align: center;
  color: #909399;
  margin: 8px 0 28px;
}
</style>
