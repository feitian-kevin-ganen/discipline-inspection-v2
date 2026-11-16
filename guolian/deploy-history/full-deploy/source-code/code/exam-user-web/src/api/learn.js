import request from './request'

/** LEARN-01 本人可见资料列表 */
export const getMaterials = (params) => request.get('/app/learn/materials', { params })

/** LEARN-02 资料详情 + 本人学习断点 */
export const getMaterialDetail = (materialId) => request.get(`/app/learn/materials/${materialId}`)

/** LEARN-03 获取资料文件地址 */
export const getMaterialFile = (materialId) => request.get(`/app/learn/materials/${materialId}/file`)

/** LEARN-04 学习进度心跳上报 */
export const sendHeartbeat = (data) => request.post('/app/learn/heartbeat', data, { silent: true })

/** LEARN-05 断点续学进度 */
export const getLearnRecord = (materialId) => request.get(`/app/learn/records/${materialId}`)

/** LEARN-06 学习任务待办 */
export const getLearnTasks = (params) => request.get('/app/learn/tasks', { params })

/** LSTAT-01 个人学习统计 */
export const getPersonalStat = () => request.get('/app/lstat/personal')
