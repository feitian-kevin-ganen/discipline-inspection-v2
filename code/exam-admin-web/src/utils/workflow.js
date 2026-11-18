// 审批流程公共展示常量

export const FLOW_STATUS = {
  RUNNING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  STOPPED: { label: '已终止', type: 'info' }
}

export const STATUS_OPTIONS = Object.entries(FLOW_STATUS).map(([value, v]) => ({ value, label: v.label }))

const BIZ_LABELS = {
  ques_item_audit: '题目审核',
  material_audit: '资料审核'
}

export const bizLabel = (b) => BIZ_LABELS[b] || b || '-'
