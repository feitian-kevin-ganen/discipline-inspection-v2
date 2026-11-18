import request from '../utils/request'

// ORG 组织管理
export const orgApi = {
  tree: (params) => request.get('/admin/org/tree', { params }),
  create: (data) => request.post('/admin/org', data),
  update: (orgId, data) => request.put(`/admin/org/${orgId}`, data),
  remove: (orgId) => request.delete(`/admin/org/${orgId}`),
  setStatus: (orgId, status) => request.put(`/admin/org/${orgId}/status`, { status }),
  sort: (data) => request.put('/admin/org/sort', data),
  move: (orgId, newParentId) => request.put(`/admin/org/${orgId}/parent`, { newParentId })
}

// USER 人员管理
export const userApi = {
  page: (params) => request.get('/admin/users', { params }),
  create: (data) => request.post('/admin/users', data),
  detail: (userId) => request.get(`/admin/users/${userId}`),
  update: (userId, data) => request.put(`/admin/users/${userId}`, data),
  remove: (userId) => request.delete(`/admin/users/${userId}`),
  setStatus: (userId, status) => request.put(`/admin/users/${userId}/status`, { status }),
  importUsers: (formData) => request.post('/admin/users/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  setRoles: (userId, roleCodes) => request.put(`/admin/users/${userId}/roles`, { roleCodes }),
  resetPassword: (userId) => request.put(`/admin/users/${userId}/password/reset`)
}

// LIB 学习资料
export const libApi = {
  categoryTree: () => request.get('/admin/lib/categories/tree'),
  createCategory: (data) => request.post('/admin/lib/categories', data),
  updateCategory: (catId, data) => request.put(`/admin/lib/categories/${catId}`, data),
  removeCategory: (catId) => request.delete(`/admin/lib/categories/${catId}`),
  page: (params) => request.get('/admin/lib/materials', { params }),
  create: (formData) => request.post('/admin/lib/materials', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  detail: (materialId) => request.get(`/admin/lib/materials/${materialId}`),
  previewFile: (materialId) => request.get(`/admin/lib/materials/${materialId}/preview-file`),
  update: (materialId, formData) => request.put(`/admin/lib/materials/${materialId}`, formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  remove: (materialId) => request.delete(`/admin/lib/materials/${materialId}`),
  audit: (materialId, data) => request.post(`/admin/lib/materials/${materialId}/audit`, data),
  publish: (materialId, action) => request.put(`/admin/lib/materials/${materialId}/publish`, { action }),
  setScope: (materialId, data) => request.put(`/admin/lib/materials/${materialId}/scope`, data),
  fetchUrl: (url) => request.get('/admin/lib/materials/fetch-url', { params: { url }, timeout: 30000 })
}

// LEARN 学习任务（管理端）
export const learnApi = {
  taskPage: (params) => request.get('/admin/learn/tasks', { params }),
  createTask: (data) => request.post('/admin/learn/tasks', data)
}

// LSTAT 学习统计（管理端）
export const lstatApi = {
  orgSummary: (params) => request.get('/admin/lstat/org-summary', { params }),
  orgDetail: (orgId, params) => request.get(`/admin/lstat/org-summary/${orgId}`, { params }),
  ranking: (params) => request.get('/admin/lstat/ranking', { params }),
  completedMaterials: (params) => request.get('/admin/lstat/completed-materials', { params }),
  exportExcel: (params) => request.get('/admin/lstat/export', { params })
}

// QUES 题库
export const quesApi = {
  bankTree: () => request.get('/admin/ques/banks/tree'),
  createBank: (data) => request.post('/admin/ques/banks', data),
  updateBank: (bankId, data) => request.put(`/admin/ques/banks/${bankId}`, data),
  removeBank: (bankId) => request.delete(`/admin/ques/banks/${bankId}`),
  page: (params) => request.get('/admin/ques/items', { params }),
  create: (data) => request.post('/admin/ques/items', data),
  detail: (itemId) => request.get(`/admin/ques/items/${itemId}`),
  update: (itemId, data) => request.put(`/admin/ques/items/${itemId}`, data),
  remove: (itemId, hard, config) => request.delete(`/admin/ques/items/${itemId}`, { params: { hard }, ...(config || {}) }),
  importItems: (formData) => request.post('/admin/ques/items/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  dupCheck: (data) => request.post('/admin/ques/items/dup-check', data),
  audit: (itemId, data) => request.post(`/admin/ques/items/${itemId}/audit`, data)
}

// AI 辅助生题
export const aiGenApi = {
  materials: (categoryId) => request.get('/admin/ques/ai-gen/materials', { params: { categoryId } }),
  createTasks: (data) => request.post('/admin/ques/ai-gen/tasks', data),
  tasks: () => request.get('/admin/ques/ai-gen/tasks'),
  removeTask: (taskId) => request.delete(`/admin/ques/ai-gen/tasks/${taskId}`),
  questions: (taskId) => request.get(`/admin/ques/ai-gen/tasks/${taskId}/questions`),
  updateQuestion: (questionId, data) => request.put(`/admin/ques/ai-gen/questions/${questionId}`, data),
  adopt: (questionId) => request.post(`/admin/ques/ai-gen/questions/${questionId}/adopt`),
  discard: (questionId) => request.post(`/admin/ques/ai-gen/questions/${questionId}/discard`)
}

// PAPER 试卷
export const paperApi = {
  page: (params) => request.get('/admin/papers', { params }),
  nameCheck: (params) => request.get('/admin/papers/name-check', { params }),
  create: (data) => request.post('/admin/papers', data),
  detail: (paperId) => request.get(`/admin/papers/${paperId}`),
  update: (paperId, data) => request.put(`/admin/papers/${paperId}`, data),
  remove: (paperId) => request.delete(`/admin/papers/${paperId}`),
  setQuestions: (paperId, questions) => request.put(`/admin/papers/${paperId}/questions`, { questions }),
  preview: (paperId) => request.get(`/admin/papers/${paperId}/preview`),
  copy: (paperId, paperName) => request.post(`/admin/papers/${paperId}/copy`, { paperName }),
  seal: (paperId) => request.put(`/admin/papers/${paperId}/seal`),
  setGradeRules: (paperId, gradeRules) => request.put(`/admin/papers/${paperId}/grade-rules`, { gradeRules })
}

// EXAM 测试场次
export const examApi = {
  page: (params) => request.get('/admin/exams', { params }),
  create: (data) => request.post('/admin/exams', data),
  detail: (examId) => request.get(`/admin/exams/${examId}`),
  update: (examId, data) => request.put(`/admin/exams/${examId}`, data),
  remove: (examId) => request.delete(`/admin/exams/${examId}`),
  setStatus: (examId, action) => request.put(`/admin/exams/${examId}/status`, { action }),
  genCandidates: (examId, scopes) => request.post(`/admin/exams/${examId}/candidates/scope`, { scopes }),
  candidatePage: (examId, params) => request.get(`/admin/exams/${examId}/candidates`, { params }),
  addCandidates: (examId, userIds) => request.post(`/admin/exams/${examId}/candidates`, { userIds }),
  removeCandidates: (examId, userIds) => request.delete(`/admin/exams/${examId}/candidates`, { data: { userIds } }),
  notify: (examId, data) => request.post(`/admin/exams/${examId}/notify`, data),
  extend: (examId, extendMin) => request.put(`/admin/exams/${examId}/extend`, { extendMin }),
  forceSubmit: (examId, data) => request.post(`/admin/exams/${examId}/force-submit`, data),
  retake: (examId, data) => request.put(`/admin/exams/${examId}/retake`, data)
}

// MARK 判分阅卷
export const markApi = {
  taskPage: (params) => request.get('/admin/mark/tasks', { params }),
  claim: (taskId) => request.post(`/admin/mark/tasks/${taskId}/claim`),
  items: (taskId, params) => request.get(`/admin/mark/tasks/${taskId}/items`, { params }),
  submitScores: (data) => request.post('/admin/mark/scores', data),
  reviewQueue: (params) => request.get('/admin/mark/review-queue', { params }),
  submitReview: (data) => request.post('/admin/mark/review', data),
  publish: (examId) => request.post(`/admin/mark/exams/${examId}/publish`, { confirm: true })
}

// STAT 测试统计
export const statApi = {
  overview: (examId) => request.get(`/admin/stat/exams/${examId}/overview`),
  orgSummary: (examId, params) => request.get(`/admin/stat/exams/${examId}/org-summary`, { params }),
  orgDetail: (examId, orgId, params) => request.get(`/admin/stat/exams/${examId}/org-summary/${orgId}`, { params }),
  scoreSegments: (examId, params) => request.get(`/admin/stat/exams/${examId}/score-segments`, { params }),
  gradeDist: (examId, params) => request.get(`/admin/stat/exams/${examId}/grade-distribution`, { params }),
  questionAnalysis: (examId, params) => request.get(`/admin/stat/exams/${examId}/question-analysis`, { params }),
  exportExcel: (examId, params) => request.get(`/admin/stat/exams/${examId}/export`, { params })
}

// SYS 系统管理
export const sysApi = {
  configs: (params) => request.get('/admin/sys/configs', { params }),
  saveConfigs: (configs) => request.put('/admin/sys/configs', { configs }),
  loginLogs: (params) => request.get('/admin/sys/logs/login', { params }),
  operationLogs: (params) => request.get('/admin/sys/logs/operation', { params }),
  examAbnormalLogs: (params) => request.get('/admin/sys/logs/exam-abnormal', { params }),
  noticePage: (params) => request.get('/admin/sys/notices', { params }),
  createNotice: (data) => request.post('/admin/sys/notices', data)
}

// 通用图片上传（富文本内嵌图）
export const fileApi = {
  uploadImage: (formData) => request.post('/admin/files/image', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
