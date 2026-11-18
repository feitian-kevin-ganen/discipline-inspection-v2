<template>
  <div class="page">
    <van-nav-bar title="修改密码" :left-arrow="!force" @click-left="onBack" />
    <van-notice-bar
      v-if="force"
      type="warning"
      wrapable
      text="首次登录须修改初始密码，修改成功后需重新登录。"
    />
    <van-form @submit="onSubmit">
      <van-cell-group inset class="form-group">
        <van-field
          v-model="form.oldPassword"
          type="password"
          label="原密码"
          placeholder="请输入原密码"
          :rules="[{ required: true, message: '请输入原密码' }]"
        />
        <van-field
          v-model="form.newPassword"
          type="password"
          label="新密码"
          placeholder="8-32位，须含字母和数字"
          :rules="[
            { required: true, message: '请输入新密码' },
            { validator: validPwd, message: '8-32位，须含字母和数字' }
          ]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入新密码"
          :rules="[
            { required: true, message: '请再次输入新密码' },
            { validator: validConfirm, message: '两次输入的密码不一致' }
          ]"
        />
      </van-cell-group>
      <div class="submit-btn">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          确认修改
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog } from 'vant'
import { changePassword } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const force = computed(() => auth.mustChangePassword)
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function validPwd(val) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(val)
}

function validConfirm(val) {
  return val === form.newPassword
}

function onBack() {
  router.back()
}

async function onSubmit() {
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    // 修改成功后服务端吊销全部 token，需重新登录
    auth.clearLocal()
    await showDialog({ title: '修改成功', message: '密码已修改，请使用新密码重新登录' })
    router.replace('/login')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-group {
  margin-top: 16px;
}

.submit-btn {
  margin: 24px 16px;
}
</style>
