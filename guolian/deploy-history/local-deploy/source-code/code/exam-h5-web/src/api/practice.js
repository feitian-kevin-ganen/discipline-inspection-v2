import service from '../utils/request'

// PRAC-00 一级法规分类列表（练习发起页分类选择）
export function getPracticeBanks() {
  return service.get('/app/practice/banks')
}

// PRAC-01 发起练习（无状态抽题）
export function startPractice(body) {
  return service.post('/app/practice/start', body)
}

// PRAC-02 提交单题作答（即时反馈正误 + 解析）
export function answerPractice(body) {
  return service.post('/app/practice/answer', body)
}

// PRAC-03 本人练习记录分页（onlyWrong=true 错题回顾含解析）
export function getPracticeRecords(params) {
  return service.get('/app/practice/records', { params })
}

// PRAC-04 本人练习统计
export function getPracticeStat(params) {
  return service.get('/app/practice/stat', { params })
}
