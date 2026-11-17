import service from '../utils/request'

// LEARN-01 可见资料列表
export function getMaterials(params) {
  return service.get('/app/learn/materials', { params })
}

// LEARN-02 资料详情 + 断点
export function getMaterialDetail(materialId) {
  return service.get(`/app/learn/materials/${materialId}`)
}

// LEARN-03 获取资料文件地址
export function getMaterialFile(materialId) {
  return service.get(`/app/learn/materials/${materialId}/file`)
}

// LEARN-04 学习进度心跳上报（静默，不打错误 toast）
export function heartbeat(body) {
  return service.post('/app/learn/heartbeat', body, { __silent: true })
}

// LEARN-05 断点续学进度
export function getLearnRecord(materialId) {
  return service.get(`/app/learn/records/${materialId}`)
}

// LEARN-06 学习任务待办
export function getLearnTasks(params) {
  return service.get('/app/learn/tasks', { params })
}

// LSTAT-01 个人学习统计
export function getPersonalStat(params) {
  return service.get('/app/lstat/personal', { params })
}
