// 通用字典与枚举映射（值以 API 文档为准）
export const ROLES = [
  { code: 'SUPER_ADMIN', name: '超级管理员' },
  { code: 'HQ_ADMIN', name: '总部管理员' },
  { code: 'L2_ADMIN', name: '二级管理员' },
  { code: 'SUB_ADMIN', name: '子企业管理员' },
  { code: 'MARKER', name: '阅卷教师' },
  { code: 'EXAMINEE', name: '考生' }
]
export const roleName = (code) => (ROLES.find((r) => r.code === code) || {}).name || code

export const ORG_TYPES = [
  { value: 'HQ', label: '集团总部' },
  { value: 'L2', label: '二级集团' },
  { value: 'SUB', label: '子企业' },
  { value: 'DEPT', label: '部门' }
]
export const orgTypeName = (v) => (ORG_TYPES.find((t) => t.value === v) || {}).label || v

export const USER_STATUS = {
  0: { label: '停用', type: 'info' },
  1: { label: '正常', type: 'success' },
  2: { label: '锁定', type: 'danger' }
}

export const MATERIAL_TYPES = {
  DOC: { label: '文档', type: 'primary' },
  VIDEO: { label: '视频', type: 'warning' },
  RICHTEXT: { label: '图文', type: 'success' }
}

export const MATERIAL_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_AUDIT: { label: '待审核', type: 'warning' },
  APPROVED: { label: '审核通过', type: 'primary' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下架', type: 'danger' }
}

export const SCOPE_TYPES = {
  ALL: '全集团',
  L2_SUB: '指定二级集团及下属',
  SPECIFIC: '指定单位'
}

export const QUES_TYPES = {
  SINGLE: { label: '单选题', type: 'primary' },
  MULTI: { label: '多选题', type: 'warning' },
  JUDGE: { label: '判断题', type: 'success' },
  FILL: { label: '填空题', type: 'danger' },
  ESSAY: { label: '简答题', type: 'info' }
}

export const DIFFICULTIES = {
  EASY: { label: '简单', type: 'success' },
  MEDIUM: { label: '中等', type: 'warning' },
  HARD: { label: '困难', type: 'danger' }
}

export const QUES_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_AUDIT: { label: '待审核', type: 'warning' },
  ENABLED: { label: '已启用', type: 'success' },
  DISABLED: { label: '已停用', type: 'danger' }
}

export const PAPER_TYPES = {
  FIXED: { label: '固定卷', type: 'primary' },
  RANDOM: { label: '随机策略卷', type: 'warning' }
}

export const PAPER_STATUS = {
  EDITING: { label: '编辑中', type: 'info' },
  READY: { label: '待使用', type: 'primary' },
  SEALED: { label: '已封存', type: 'success' }
}

export const MULTI_SCORE_RULES = {
  ALL_OR_NOTHING: '全对得分',
  HALF_ON_MISS: '漏选得半分，错选不得分',
  NONE_ON_MISS: '错选、漏选均不得分'
}

export const EXAM_STATUS = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'warning' },
  ENDED: { label: '已结束', type: 'primary' },
  SCORED_PUBLISHED: { label: '已发布成绩', type: 'success' },
  CANCELED: { label: '已取消', type: 'danger' }
}

export const SHEET_STATUS = {
  NOT_ENTERED: { label: '未进入', type: 'info' },
  IN_EXAM: { label: '考试中', type: 'warning' },
  SUBMITTED: { label: '已交卷', type: 'success' },
  FORCED: { label: '强制收卷', type: 'danger' },
  ABSENT: { label: '缺考', type: 'info' }
}

export const MARK_TASK_STATUS = {
  PENDING: { label: '待领取', type: 'info' },
  DOING: { label: '阅卷中', type: 'warning' },
  DONE: { label: '已完成', type: 'success' }
}

export const REVIEW_TYPES = {
  FILL_SIMILAR: '填空疑似命中',
  DOUBLE_MARK_GAP: '双评分差仲裁'
}

export const NOTICE_TYPES = {
  EXAM: '考试通知',
  SCORE: '成绩发布',
  LEARN_TASK: '学习任务',
  GENERAL: '通用通知'
}

export const ABNORMAL_TYPES = {
  FORCE_SUBMIT: '强制收卷',
  SWITCH_EXCEED: '切屏超限',
  KICK_OFFLINE: '异地踢出'
}

export const PERIOD_TYPES = [
  { value: 'MONTH', label: '本月' },
  { value: 'QUARTER', label: '本季度' },
  { value: 'YEAR', label: '本年' },
  { value: 'CUSTOM', label: '自定义' }
]

export const LEARN_STATUS = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  IN_PROGRESS: { label: '学习中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' }
}
