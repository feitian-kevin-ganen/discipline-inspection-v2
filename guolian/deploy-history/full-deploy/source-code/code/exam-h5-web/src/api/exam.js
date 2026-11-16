import service from '../utils/request'

// TAKE-01 待考/历史考试列表
export function getExams(params) {
  return service.get('/app/exams', { params })
}

// TAKE-02 进入考试（须知确认）
export function enterExam(examId, body) {
  return service.post(`/app/exams/${examId}/enter`, body)
}

// TAKE-03 服务端校时（静默）
export function getServerTime() {
  return service.get('/app/time', { __silent: true })
}

// TAKE-04 拉取试卷（脱敏）
export function getPaper(examId, sheetId) {
  return service.get(`/app/exams/${examId}/paper`, { params: { sheetId } })
}

// TAKE-05 批量/增量保存答案（静默，错误由调用方入本地队列）
export function saveAnswers(sheetId, body) {
  return service.put(`/app/sheets/${sheetId}/answers`, body, { __silent: true, timeout: 10000 })
}

// TAKE-06 切屏/离开页面上报（静默）
export function reportScreenEvent(sheetId, body) {
  return service.post(`/app/sheets/${sheetId}/screen-events`, body, { __silent: true })
}

// TAKE-07 交卷（静默，失败由调用方退避重试）
export function submitSheet(sheetId, body) {
  return service.post(`/app/sheets/${sheetId}/submit`, body, { __silent: true, timeout: 15000 })
}

// TAKE-08 答卷状态查询（断线续考）
export function getSheet(sheetId) {
  return service.get(`/app/sheets/${sheetId}`)
}

// TAKE-09 成绩查询
export function getExamResult(examId) {
  return service.get(`/app/exams/${examId}/result`)
}

// STAT-08 个人历次成绩单
export function getMyTranscripts(params) {
  return service.get('/app/stat/my-transcripts', { params })
}
