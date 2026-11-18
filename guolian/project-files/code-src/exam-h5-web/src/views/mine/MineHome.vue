<template>
  <div>
    <van-nav-bar title="我的" />
    <div class="user-panel">
      <van-icon name="user-circle-o" size="56" color="#fff" />
      <div class="user-info">
        <div class="user-name">{{ user?.name || '-' }}</div>
        <div class="user-meta">工号 {{ user?.jobNo || '-' }}</div>
        <div class="user-meta">{{ user?.orgName || '-' }}</div>
      </div>
    </div>

    <van-cell-group inset class="menu-group">
      <van-cell title="我的成绩" icon="medal-o" is-link to="/mine/scores" />
      <van-cell title="学习统计" icon="bar-chart-o" is-link to="/mine/learn-stat" />
      <van-cell title="日常练习" icon="aim" is-link to="/practice" />
      <van-cell title="练习记录" icon="todo-list-o" is-link to="/practice/records" />
      <van-cell title="练习统计" icon="bar-chart-o" is-link to="/practice/stat" />
      <van-cell title="修改密码" icon="lock" is-link to="/change-password" />
    </van-cell-group>

    <div class="logout-btn">
      <van-button round block type="danger" plain :loading="loggingOut" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog } from 'vant'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)
const user = computed(() => auth.user)

onMounted(() => {
  // 进页刷新一次用户信息
  auth.fetchMe().catch(() => {})
})

async function onLogout() {
  try {
    await showDialog({ title: '退出登录', message: '确定退出当前账号吗？', showCancelButton: true })
  } catch (e) {
    return
  }
  loggingOut.value = true
  try {
    await auth.logout()
    router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.user-panel {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 20px;
  background: linear-gradient(135deg, #b3271e, #d4574e);
}

.user-name {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.user-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 4px;
}

.menu-group {
  margin-top: 12px;
}

.logout-btn {
  margin: 32px 16px;
}
</style>
