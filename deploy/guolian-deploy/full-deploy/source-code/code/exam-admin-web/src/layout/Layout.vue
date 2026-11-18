<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">在线考试系统管理端</div>
      <el-menu
        :default-active="activePath"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <template v-for="menu in menus" :key="menu.path || menu.title">
          <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.title">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="breadcrumb">{{ route.meta.title || '' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            {{ auth.user?.name || '用户' }}
            <span class="org">{{ auth.user?.orgName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const ADMIN = ['SUPER_ADMIN', 'HQ_ADMIN', 'L2_ADMIN', 'SUB_ADMIN']
const MARK = [...ADMIN, 'MARKER']
const SYS = ['SUPER_ADMIN', 'HQ_ADMIN']

const MENU_CONFIG = [
  { title: '首页', path: '/dashboard', icon: 'HomeFilled' },
  {
    title: '组织管理', icon: 'OfficeBuilding', roles: ADMIN,
    children: [
      { title: '组织机构', path: '/org/tree' },
      { title: '人员管理', path: '/org/users' }
    ]
  },
  {
    title: '学习管理', icon: 'Reading', roles: ADMIN,
    children: [
      { title: '资料分类', path: '/learn/categories' },
      { title: '资料管理', path: '/learn/materials' },
      { title: '学习任务', path: '/learn/tasks' },
      { title: '学习统计', path: '/learn/stat' }
    ]
  },
  {
    title: '题库管理', icon: 'Collection', roles: ADMIN,
    children: [{ title: '题目管理', path: '/ques/items' }]
  },
  {
    title: '试卷管理', icon: 'Document', roles: ADMIN,
    children: [{ title: '试卷列表', path: '/papers' }]
  },
  {
    title: '考试管理', icon: 'AlarmClock', roles: ADMIN,
    children: [{ title: '考试场次', path: '/exams' }]
  },
  {
    title: '阅卷管理', icon: 'EditPen', roles: MARK,
    children: [
      { title: '阅卷任务', path: '/mark/tasks' },
      { title: '复核队列', path: '/mark/review' }
    ]
  },
  {
    title: '考试统计', icon: 'DataAnalysis', roles: ADMIN,
    children: [{ title: '统计分析', path: '/stat' }]
  },
  {
    title: '系统管理', icon: 'Setting', roles: ADMIN,
    children: [
      { title: '参数配置', path: '/sys/configs', roles: SYS },
      { title: '登录日志', path: '/sys/logs/login', roles: SYS },
      { title: '操作日志', path: '/sys/logs/operation', roles: SYS },
      { title: '考试异常日志', path: '/sys/logs/exam-abnormal', roles: ADMIN },
      { title: '站内通知', path: '/sys/notices', roles: ADMIN }
    ]
  }
]

const menus = computed(() => {
  const roles = auth.roles
  const allow = (item) => !item.roles || item.roles.some((r) => roles.includes(r))
  return MENU_CONFIG.filter(allow).map((m) => {
    if (!m.children) return m
    return { ...m, children: m.children.filter(allow) }
  }).filter((m) => !m.children || m.children.length)
})

// 子页面（如 /papers/edit/5、/exams/12）不在菜单项里，用最长前缀匹配保持父菜单高亮
const activePath = computed(() => {
  const p = route.path
  const paths = []
  menus.value.forEach((m) => {
    if (m.path) paths.push(m.path)
    ;(m.children || []).forEach((c) => paths.push(c.path))
  })
  if (paths.includes(p)) return p
  let best = ''
  for (const mp of paths) {
    if (p.startsWith(mp + '/') && mp.length > best.length) best = mp
  }
  return best || p
})

onMounted(() => {
  auth.fetchMe()
})

async function onCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await auth.logout()
    router.push('/login')
  } else if (cmd === 'password') {
    router.push('/change-password')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background-color: #001529;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  background-color: #002140;
  white-space: nowrap;
  overflow: hidden;
}
.aside :deep(.el-menu) {
  border-right: none;
}
/* 选中菜单高亮：品牌蓝底色 + 左侧亮蓝指示条，与深色侧栏协调 */
.aside :deep(.el-menu-item.is-active) {
  background-color: #1668dc;
  color: #fff;
  box-shadow: inset 3px 0 0 #69b1ff;
}
.aside :deep(.el-menu-item:hover),
.aside :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.08);
}
/* 选中项在鼠标悬停时仍保持高亮 */
.aside :deep(.el-menu-item.is-active:hover) {
  background-color: #1668dc;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  height: 60px;
}
.breadcrumb {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}
.user-info .org {
  color: #909399;
  font-size: 12px;
}
.main {
  padding: 0;
  overflow-y: auto;
}
</style>
