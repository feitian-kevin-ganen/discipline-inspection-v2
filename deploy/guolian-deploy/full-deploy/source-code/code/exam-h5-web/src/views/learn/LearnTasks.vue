<template>
  <div class="page page-has-navbar">
    <van-nav-bar title="学习任务" left-arrow @click-left="$router.back()" fixed placeholder />
    <van-tabs v-model:active="active" @change="onTabChange">
      <van-tab title="待完成" name="TODO" />
      <van-tab title="已完成" name="DONE" />
    </van-tabs>
    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <van-empty v-if="!loading && list.length === 0" description="暂无学习任务" />
      <van-cell-group v-for="task in list" :key="task.taskId" inset class="task-card">
        <div class="task-head">
          <span class="task-title">{{ task.title }}</span>
          <van-tag :type="tagType(task.myStatus)">{{ taskStatusName[task.myStatus] || task.myStatus }}</van-tag>
        </div>
        <div class="text-muted task-meta">
          要求时长 {{ formatDuration(task.requiredSec) }} · 截止 {{ task.deadline || '-' }}
        </div>
        <div
          v-for="m in task.materials || []"
          :key="m.materialId"
          class="task-material"
          @click="goMaterial(m)"
        >
          <van-icon :name="iconOf(m.materialType)" class="task-material-icon" />
          <span class="task-material-title">{{ m.title }}</span>
          <van-progress
            :percentage="Math.round((m.myProgressPct || 0) * 100)"
            stroke-width="4"
            class="task-material-progress"
          />
        </div>
      </van-cell-group>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getLearnTasks } from '../../api/learn'
import { formatDuration, taskStatusName } from '../../utils/format'

const router = useRouter()
const active = ref('TODO')
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

function tagType(status) {
  return status === 'DONE' ? 'success' : status === 'EXPIRED' ? 'danger' : 'primary'
}

function iconOf(type) {
  return type === 'VIDEO' ? 'video-o' : type === 'DOC' ? 'description' : 'orders-o'
}

async function onLoad() {
  try {
    const data = await getLearnTasks({ status: active.value, pageNum: pageNum.value, pageSize })
    const items = data.list || []
    list.value = list.value.concat(items)
    if (items.length < pageSize || list.value.length >= (data.total || 0)) finished.value = true
    pageNum.value += 1
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  list.value = []
  pageNum.value = 1
  finished.value = false
  loading.value = true
  onLoad()
}

function goMaterial(m) {
  router.push(`/learn/material/${m.materialId}`)
}
</script>

<style scoped>
.task-card {
  margin-top: 12px;
  padding: 12px;
}

.task-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-title {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}

.task-meta {
  margin: 8px 0;
}

.task-material {
  display: flex;
  align-items: center;
  padding: 8px;
  background: #f7f8fa;
  border-radius: 6px;
  margin-bottom: 8px;
}

.task-material-icon {
  margin-right: 8px;
  color: #1989fa;
}

.task-material-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-material-progress {
  width: 80px;
  margin-left: 8px;
}
</style>
