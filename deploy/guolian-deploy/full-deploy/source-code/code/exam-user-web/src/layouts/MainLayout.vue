<template>
  <el-container class="layout">
    <el-header class="header" height="56px">
      <div class="brand">
        <el-icon :size="22" color="#fff"><Medal /></el-icon>
        <span>纪检监察在线考试系统 · 用户端</span>
      </div>
      <div class="right">
        <el-dropdown @command="onCommand">
          <span class="user-name">
            {{ user?.name }}（{{ user?.jobNo }}）
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container class="main-container">
      <el-aside width="200px" class="aside">
        <el-menu :default-active="activeMenu" router>
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon><span>首页工作台</span>
          </el-menu-item>
          <el-menu-item index="/learn">
            <el-icon><Reading /></el-icon><span>学习中心</span>
          </el-menu-item>
          <el-menu-item index="/learn/tasks">
            <el-icon><List /></el-icon><span>学习任务</span>
          </el-menu-item>
          <el-menu-item index="/exam">
            <el-icon><EditPen /></el-icon><span>考试中心</span>
          </el-menu-item>
          <el-menu-item index="/score">
            <el-icon><Trophy /></el-icon><span>我的成绩</span>
          </el-menu-item>
          <el-menu-item v-if="isMarker" index="/mark">
            <el-icon><Checked /></el-icon><span>阅卷工作台</span>
          </el-menu-item>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon><span>个人中心</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'
import { logout as apiLogout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const { user, isMarker } = storeToRefs(store)

const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/learn/tasks')) return '/learn/tasks'
  if (p.startsWith('/learn')) return '/learn'
  if (p.startsWith('/exam')) return '/exam'
  if (p.startsWith('/score')) return '/score'
  if (p.startsWith('/mark')) return '/mark'
  return p
})

async function onCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    try {
      await apiLogout()
    } catch (e) {
      /* 忽略登出接口异常 */
    }
    store.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1f2d3d;
  color: #fff;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
}
.user-name {
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
.main-container {
  height: calc(100% - 56px);
}
.aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}
.aside .el-menu {
  border-right: none;
}
/* 选中菜单高亮：浅蓝底 + 品牌蓝文字 + 左侧指示条，与白底侧栏协调 */
.aside :deep(.el-menu-item.is-active) {
  background-color: #eaf2fe;
  color: #1565c0;
  font-weight: 600;
  box-shadow: inset 3px 0 0 #1565c0;
}
.aside :deep(.el-menu-item:hover) {
  background-color: #f5f8fd;
}
/* 选中项在鼠标悬停时仍保持高亮 */
.aside :deep(.el-menu-item.is-active:hover) {
  background-color: #eaf2fe;
}
.content {
  overflow-y: auto;
}
</style>
