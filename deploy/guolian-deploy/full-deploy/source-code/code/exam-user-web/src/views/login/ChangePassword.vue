<template>
  <div class="pwd-page">
    <el-card class="pwd-card">
      <div class="title">
        <h2>{{ store.mustChangePassword ? '首次登录请修改密码' : '修改密码' }}</h2>
        <p v-if="store.mustChangePassword">您正在使用初始密码，为保障账号安全，请先设置新密码</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-width="90px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="onSubmit">确认修改</el-button>
        </el-form-item>
      </el-form>
      <p class="tip">新密码须 8-32 位且包含字母和数字；修改成功后需重新登录。</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateNew = (rule, value, callback) => {
  if (!/^(?=.*[A-Za-z])(?=.*\d)[\S]{8,32}$/.test(value || '')) {
    callback(new Error('须 8-32 位且同时包含字母和数字'))
  } else if (value === form.oldPassword) {
    callback(new Error('新密码不能与原密码相同'))
  } else {
    callback()
  }
}
const validateConfirm = (rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validateNew, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    // 修改成功后服务端吊销全部 token，回登录页
    store.logout()
    router.push('/login')
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
.pwd-card {
  width: 460px;
}
.title {
  text-align: center;
  margin-bottom: 20px;
}
.title h2 {
  margin: 0 0 6px;
}
.title p {
  margin: 0;
  color: #e6a23c;
  font-size: 13px;
}
.tip {
  color: #909399;
  font-size: 12px;
  text-align: center;
}
</style>
