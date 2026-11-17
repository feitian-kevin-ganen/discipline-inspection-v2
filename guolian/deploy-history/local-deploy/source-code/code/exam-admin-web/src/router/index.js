import { createRouter, createWebHistory } from 'vue-router'

const ADMIN_ROLES = ['SUPER_ADMIN', 'HQ_ADMIN', 'L2_ADMIN', 'SUB_ADMIN']
const MARK_ROLES = [...ADMIN_ROLES, 'MARKER']
const SYS_ROLES = ['SUPER_ADMIN', 'HQ_ADMIN']

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/login/Login.vue'), meta: { title: '登录' } },
  { path: '/change-password', name: 'ChangePassword', component: () => import('../views/login/ChangePassword.vue'), meta: { title: '修改密码' } },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Index.vue'), meta: { title: '首页' } },
      // 组织管理
      { path: 'org/tree', name: 'OrgTree', component: () => import('../views/org/OrgTree.vue'), meta: { title: '组织机构', roles: ADMIN_ROLES } },
      { path: 'org/users', name: 'UserList', component: () => import('../views/org/UserList.vue'), meta: { title: '人员管理', roles: ADMIN_ROLES } },
      // 学习管理
      { path: 'learn/categories', name: 'LibCategory', component: () => import('../views/learn/CategoryTree.vue'), meta: { title: '资料分类', roles: ADMIN_ROLES } },
      { path: 'learn/materials', name: 'MaterialList', component: () => import('../views/learn/MaterialList.vue'), meta: { title: '资料管理', roles: ADMIN_ROLES } },
      { path: 'learn/tasks', name: 'LearnTask', component: () => import('../views/learn/TaskList.vue'), meta: { title: '学习任务', roles: ADMIN_ROLES } },
      { path: 'learn/stat', name: 'LearnStat', component: () => import('../views/learn/LearnStat.vue'), meta: { title: '学习统计', roles: ADMIN_ROLES } },
      // 题库管理
      { path: 'ques/items', name: 'QuesItems', component: () => import('../views/ques/ItemList.vue'), meta: { title: '题目管理', roles: ADMIN_ROLES } },
      { path: 'ques/items/edit/:id?', name: 'QuesItemEdit', component: () => import('../views/ques/ItemForm.vue'), meta: { title: '编辑题目', roles: ADMIN_ROLES } },
      // 试卷管理
      { path: 'papers', name: 'PaperList', component: () => import('../views/paper/PaperList.vue'), meta: { title: '试卷管理', roles: ADMIN_ROLES } },
      { path: 'papers/edit/:id?', name: 'PaperEdit', component: () => import('../views/paper/PaperEdit.vue'), meta: { title: '编辑试卷', roles: ADMIN_ROLES } },
      { path: 'papers/:id/preview', name: 'PaperPreview', component: () => import('../views/paper/PaperPreview.vue'), meta: { title: '试卷预览', roles: ADMIN_ROLES } },
      // 考试管理
      { path: 'exams', name: 'ExamList', component: () => import('../views/exam/ExamList.vue'), meta: { title: '考试场次', roles: ADMIN_ROLES } },
      { path: 'exams/edit/:id?', name: 'ExamEdit', component: () => import('../views/exam/ExamEdit.vue'), meta: { title: '编辑场次', roles: ADMIN_ROLES } },
      { path: 'exams/:id', name: 'ExamDetail', component: () => import('../views/exam/ExamDetail.vue'), meta: { title: '场次详情', roles: ADMIN_ROLES } },
      // 阅卷管理
      { path: 'mark/tasks', name: 'MarkTasks', component: () => import('../views/mark/TaskList.vue'), meta: { title: '阅卷任务', roles: MARK_ROLES } },
      { path: 'mark/tasks/:taskId', name: 'MarkItems', component: () => import('../views/mark/MarkItems.vue'), meta: { title: '在线阅卷', roles: MARK_ROLES } },
      { path: 'mark/review', name: 'ReviewQueue', component: () => import('../views/mark/ReviewQueue.vue'), meta: { title: '复核队列', roles: MARK_ROLES } },
      // 考试统计
      { path: 'stat', name: 'ExamStat', component: () => import('../views/stat/ExamStat.vue'), meta: { title: '考试统计', roles: ADMIN_ROLES } },
      // 系统管理
      { path: 'sys/configs', name: 'SysConfigs', component: () => import('../views/sys/ConfigList.vue'), meta: { title: '参数配置', roles: SYS_ROLES } },
      { path: 'sys/logs/login', name: 'LoginLogs', component: () => import('../views/sys/LoginLogs.vue'), meta: { title: '登录日志', roles: SYS_ROLES } },
      { path: 'sys/logs/operation', name: 'OperationLogs', component: () => import('../views/sys/OperationLogs.vue'), meta: { title: '操作日志', roles: SYS_ROLES } },
      { path: 'sys/logs/exam-abnormal', name: 'ExamAbnormalLogs', component: () => import('../views/sys/ExamAbnormalLogs.vue'), meta: { title: '考试异常日志', roles: ADMIN_ROLES } },
      { path: 'sys/notices', name: 'NoticeList', component: () => import('../views/sys/NoticeList.vue'), meta: { title: '站内通知', roles: ADMIN_ROLES } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/error/NotFound.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach((to) => {
  document.title = (to.meta.title ? to.meta.title + ' - ' : '') + '纪检监察人员在线考试系统'
  const token = localStorage.getItem('accessToken')
  if (to.path === '/login') {
    return true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles) {
    const user = JSON.parse(localStorage.getItem('userInfo') || 'null')
    const roles = (user && user.roles) || []
    if (!roles.some((r) => to.meta.roles.includes(r))) {
      return { path: '/dashboard' }
    }
  }
  return true
})

export default router
