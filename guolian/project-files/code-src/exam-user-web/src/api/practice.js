import request from './request'

// PRAC-00 一级法规分类列表（练习发起页分类选择）
export function getPracticeBanks() {
  return request.get('/app/practice/banks')
}

// PRAC-01 发起练习（无状态抽题）
export function startPractice(body) {
  return request.post('/app/practice/start', body)
}

// PRAC-02 提交单题作答（即时反馈正误 + 解析）
export function answerPractice(body) {
  return request.post('/app/practice/answer', body)
}

// PRAC-03 本人练习记录分页（onlyWrong=true 错题回顾含解析）
export function getPracticeRecords(params) {
  return request.get('/app/practice/records', { params })
}

// PRAC-04 本人练习统计
export function getPracticeStat(params) {
  return request.get('/app/practice/stat', { params })
}
