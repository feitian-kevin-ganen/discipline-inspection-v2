<template>
  <div class="pwd-page">
    <div class="pwd-box">
      <h2>修改密码</h2>
      <p class="tip">新密码须 8-32 位，且同时包含字母和数字。修改成功后需重新登录。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" size="large">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">确认修改</el-button>
          <el-button @click="router.push('/')">返回首页</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const pwdPattern = /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/
const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: pwdPattern, message: '8-32 位，须含字母和数字', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (r, v, cb) => (v === form.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))),
      trigger: 'blur'
    }
  ]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await request.put('/auth/password', {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    localStorage.removeItem('mgmt_accessToken')
    localStorage.removeItem('mgmt_refreshToken')
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    router.push('/login')
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pwd-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
.pwd-box {
  width: 460px;
  background: #fff;
  border-radius: 8px;
  padding: 36px;
}
h2 {
  margin: 0 0 8px;
  text-align: center;
}
.tip {
  color: #909399;
  font-size: 13px;
  margin-bottom: 24px;
}
</style>
