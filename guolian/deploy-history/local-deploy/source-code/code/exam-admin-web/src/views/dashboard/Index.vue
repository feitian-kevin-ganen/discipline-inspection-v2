<template>
  <div class="page-container">
    <div class="page-card welcome">
      <h2>欢迎使用纪检监察人员在线考试系统</h2>
      <p>{{ greeting }}，{{ auth.user?.name }}（{{ auth.user?.orgName }}）</p>
      <p class="muted">请从左侧菜单进入各功能模块：组织管理、学习管理、题库管理、试卷管理、考试管理、阅卷管理、考试统计、系统管理。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../../store/auth'

const auth = useAuthStore()
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
</script>

<style scoped>
.welcome {
  padding: 40px;
}
.muted {
  color: #909399;
}
</style>
