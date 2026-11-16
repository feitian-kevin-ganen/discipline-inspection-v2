import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/Login.vue') },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/login/ChangePassword.vue')
  },
  // 答题页全屏，不带侧边布局
  {
    path: '/exam/take/:examId',
    name: 'TakeExam',
    component: () => import('@/views/exam/TakeExam.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/home' },
      { path: 'home', name: 'Dashboard', component: () => import('@/views/home/Dashboard.vue') },
      { path: 'learn', name: 'LearnCenter', component: () => import('@/views/learn/LearnCenter.vue') },
      {
        path: 'learn/material/:materialId',
        name: 'MaterialDetail',
        component: () => import('@/views/learn/MaterialDetail.vue')
      },
      { path: 'learn/tasks', name: 'LearnTasks', component: () => import('@/views/learn/LearnTasks.vue') },
      { path: 'exam', name: 'ExamList', component: () => import('@/views/exam/ExamList.vue') },
      {
        path: 'exam/notice/:examId',
        name: 'ExamNotice',
        component: () => import('@/views/exam/ExamNotice.vue')
      },
      {
        path: 'exam/result/:examId',
        name: 'ExamResult',
        component: () => import('@/views/exam/ExamResult.vue')
      },
      { path: 'score', name: 'ScoreList', component: () => import('@/views/score/ScoreList.vue') },
      {
        path: 'score/:examId',
        name: 'ScoreDetail',
        component: () => import('@/views/score/ScoreDetail.vue')
      },
      {
        path: 'mark',
        name: 'MarkTasks',
        component: () => import('@/views/mark/MarkTasks.vue'),
        meta: { roles: ['MARKER'] }
      },
      {
        path: 'mark/:taskId',
        name: 'MarkPaper',
        component: () => import('@/views/mark/MarkPaper.vue'),
        meta: { roles: ['MARKER'] }
      },
      { path: 'profile', name: 'Profile', component: () => import('@/views/profile/Profile.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.path === '/login') return true
  if (!store.isLoggedIn) {
    return { path: '/login', query: to.fullPath !== '/home' ? { redirect: to.fullPath } : {} }
  }
  // 首次登录强制改密
  if (store.mustChangePassword && to.path !== '/change-password') {
    return '/change-password'
  }
  if (to.meta.roles && !to.meta.roles.some((r) => store.roles.includes(r))) {
    return '/home'
  }
  return true
})

export default router
