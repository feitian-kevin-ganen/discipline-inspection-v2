<template>
  <el-timeline v-if="list && list.length" class="approval-timeline">
    <el-timeline-item
      v-for="(item, index) in list"
      :key="index"
      :type="nodeType(item)"
      :hollow="!item.endTime"
      :timestamp="fmtTime(item.endTime || item.startTime)"
      placement="top"
    >
      <div class="node-name">{{ item.taskName || '-' }}</div>
      <div class="node-detail">
        <span>办理人：{{ item.assignee || '-' }}</span>
        <span v-if="item.comment" class="comment">意见：{{ item.comment }}</span>
      </div>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无流转记录" :image-size="60" />
</template>

<script setup>
import { fmtTime } from '../../utils/format'

defineProps({
  list: { type: Array, default: () => [] }
})

function nodeType(item) {
  const comment = (item.comment || '').toString()
  if (comment.startsWith('驳回')) return 'danger'
  if (!item.endTime) return 'warning'
  return 'success'
}
</script>

<style scoped>
.approval-timeline {
  padding-left: 4px;
}
.node-name {
  font-weight: 600;
}
.node-detail {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.comment {
  word-break: break-all;
}
</style>
