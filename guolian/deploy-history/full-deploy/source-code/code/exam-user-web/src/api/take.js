import request from './request'

/** TAKE-01 待考/历史考试列表；silent 用于首页工作台等非强提示场景 */
export const getMyExams = (params, config = {}) => request.get('/app/exams', { params, ...config })

/** TAKE-02 进入考试（须知确认） */
export const enterExam = (examId, data) => request.post(`/app/exams/${examId}/enter`, data)

/** TAKE-03 服务端校时 */
export const getServerTime = () => request.get('/app/time', { silent: true })

/** TAKE-04 拉取试卷（脱敏） */
export const getExamPaper = (examId, sheetId) =>
  request.get(`/app/exams/${examId}/paper`, { params: { sheetId } })

/** TAKE-05 批量/增量保存答案 */
export const saveAnswers = (sheetId, data) =>
  request.put(`/app/sheets/${sheetId}/answers`, data, { silent: true })

/** TAKE-06 切屏/离开页面上报 */
export const reportScreenEvent = (sheetId, data) =>
  request.post(`/app/sheets/${sheetId}/screen-events`, data, { silent: true })

/** TAKE-07 交卷（幂等） */
export const submitSheet = (sheetId, data) =>
  request.post(`/app/sheets/${sheetId}/submit`, data, { silent: true })

/** TAKE-08 答卷状态查询（断线续考） */
export const getSheet = (sheetId) => request.get(`/app/sheets/${sheetId}`, { silent: true })

/** TAKE-09 成绩查询（发布后） */
export const getExamResult = (examId) => request.get(`/app/exams/${examId}/result`, { silent: true })

/** STAT-08 个人历次成绩单 */
export const getMyTranscripts = (params) => request.get('/app/stat/my-transcripts', { params })
