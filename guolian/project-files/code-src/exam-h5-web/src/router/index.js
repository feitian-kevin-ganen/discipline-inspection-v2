import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/login/LoginPage.vue'), meta: { title: '登录' } },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('../views/login/ChangePassword.vue'),
    meta: { title: '修改密码', requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/learn' },
      { path: 'learn', name: 'Learn', component: () => import('../views/learn/LearnHome.vue'), meta: { title: '学习' } },
      { path: 'exam', name: 'Exam', component: () => import('../views/exam/ExamList.vue'), meta: { title: '测试' } },
      { path: 'practice', name: 'Practice', component: () => import('../views/practice/PracticeHome.vue'), meta: { title: '练习' } },
      { path: 'mine', name: 'Mine', component: () => import('../views/mine/MineHome.vue'), meta: { title: '我的' } }
    ]
  },
  {
    path: '/practice/answer',
    name: 'PracticeAnswer',
    component: () => import('../views/practice/PracticeAnswer.vue'),
    meta: { title: '练习答题', requiresAuth: true }
  },
  {
    path: '/practice/records',
    name: 'PracticeRecords',
    component: () => import('../views/practice/PracticeRecords.vue'),
    meta: { title: '练习记录', requiresAuth: true }
  },
  {
    path: '/practice/stat',
    name: 'PracticeStat',
    component: () => import('../views/practice/PracticeStat.vue'),
    meta: { title: '练习统计', requiresAuth: true }
  },
  {
    path: '/learn/tasks',
    name: 'LearnTasks',
    component: () => import('../views/learn/LearnTasks.vue'),
    meta: { title: '学习任务', requiresAuth: true }
  },
  {
    path: '/learn/material/:id',
    name: 'MaterialDetail',
    component: () => import('../views/learn/MaterialDetail.vue'),
    meta: { title: '资料详情', requiresAuth: true }
  },
  {
    path: '/exam/notice/:examId',
    name: 'ExamNotice',
    component: () => import('../views/exam/ExamNotice.vue'),
    meta: { title: '测试须知', requiresAuth: true }
  },
  {
    path: '/exam/answer/:examId',
    name: 'ExamAnswer',
    component: () => import('../views/exam/ExamAnswer.vue'),
    meta: { title: '在线答题', requiresAuth: true }
  },
  {
    path: '/exam/result/:examId',
    name: 'ExamResult',
    component: () => import('../views/exam/ExamResult.vue'),
    meta: { title: '测试结果', requiresAuth: true }
  },
  {
    path: '/mine/scores',
    name: 'MyScores',
    component: () => import('../views/mine/MyScores.vue'),
    meta: { title: '我的成绩', requiresAuth: true }
  },
  {
    path: '/mine/learn-stat',
    name: 'LearnStat',
    component: () => import('../views/mine/LearnStat.vue'),
    meta: { title: '学习统计', requiresAuth: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 纪检监察在线测试` : '纪检监察在线测试'
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLogin) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  // 首登强制改密：拦截一切页面到改密页
  if (auth.isLogin && auth.mustChangePassword && to.path !== '/change-password') {
    return { path: '/change-password' }
  }
  if (to.path === '/login' && auth.isLogin) {
    return { path: '/' }
  }
  return true
})

export default router
