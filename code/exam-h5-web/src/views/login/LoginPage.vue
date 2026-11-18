<template>
  <div class="login-page">
    <div class="login-header">
      <div class="login-logo">
        <van-icon name="shield-o" size="56" color="#fff" />
      </div>
      <div class="login-title">“纪检监察业务”学习平台</div>
    </div>
    <div v-if="ssoLoading" class="sso-loading">
      <van-loading size="28px" color="#b3271e">国联 APP 登录验证中…</van-loading>
    </div>
    <div v-else-if="ssoEntering" class="sso-loading">
      <van-loading size="28px" color="#b3271e">登录成功，正在进入…</van-loading>
    </div>
    <van-form v-else class="login-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="工号"
          placeholder="请输入工号"
          left-icon="user-o"
          :rules="[{ required: true, message: '请输入工号' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          left-icon="lock"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="login-btn">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登 录
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const ssoLoading = ref(false)
// 门户分流进入时首帧直接显示"正在进入"，不渲染登录表单，避免闪烁
const ssoEntering = ref(!!sessionStorage.getItem('sso_redirect'))
const form = reactive({ username: '', password: '' })

// 国联 APP 单点免密登录：地址 A 携带 ?ticket=xxx 跳转回来时自动登录
onMounted(async () => {
  // 门户统一入口 /login 已完成 ticket 换令牌并写入本地存储，直接跳首页
  if (sessionStorage.getItem('sso_redirect')) {
    sessionStorage.removeItem('sso_redirect')
    router.replace(route.query.redirect || '/')
    return
  }
  const ticket = route.query.ticket
  if (!ticket || typeof ticket !== 'string') return
  ssoLoading.value = true
  try {
    const data = await auth.loginByTicket(ticket.trim())
    if (data.mustChangePassword) {
      showToast('首次登录请先修改密码')
      router.replace('/change-password')
    } else {
      router.replace(route.query.redirect || '/')
    }
  } catch (e) {
    // 校验失败回退到工号密码登录，错误信息已由拦截器 toast
    showToast({ message: '单点登录失败，请使用工号密码登录', type: 'fail' })
  } finally {
    ssoLoading.value = false
  }
})

async function onSubmit() {
  loading.value = true
  try {
    const data = await auth.login(form.username.trim(), form.password)
    if (data.mustChangePassword) {
      showToast('首次登录请先修改密码')
      router.replace('/change-password')
    } else {
      router.replace(route.query.redirect || '/')
    }
  } catch (e) {
    // 凭证错误（10002，白名单接口拦截器不 toast）：统一映射为固定文案
    if (e && e.code === 10002) {
      showToast({ message: '账号或密码错误', type: 'fail' })
    } else if (e && e.code === -1) {
      // 网络异常拦截器不 toast，这里给通用提示
      showToast({ message: '网络连接异常，请稍后重试', type: 'fail' })
    }
    // 其他业务错误已由拦截器统一 toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #b3271e 0%, #d4574e 40%, #f7f8fa 40%);
  box-sizing: border-box;
  padding-top: 64px;
}

.login-header {
  text-align: center;
  color: #fff;
  margin-bottom: 32px;
}

.login-logo {
  width: 88px;
  height: 88px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 1px;
}

.login-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.85;
}

.login-btn {
  margin: 24px 16px;
}

.sso-loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}
</style>
