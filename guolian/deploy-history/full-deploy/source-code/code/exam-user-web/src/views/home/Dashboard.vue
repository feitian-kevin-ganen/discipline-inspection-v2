<template>
  <div class="page" v-loading="loading">
    <!-- 学习统计卡片 -->
    <el-row :gutter="16" class="mb16">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ fmtDuration(stat.totalEffectiveSec) }}</div>
          <div class="stat-label">累计有效学习时长</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stat.materialStats?.completed ?? 0 }}</div>
          <div class="stat-label">已完成资料</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stat.materialStats?.inProgress ?? 0 }}</div>
          <div class="stat-label">学习中资料</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ taskDoneRateText }}</div>
          <div class="stat-label">学习任务完成率</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="10">
        <!-- 待办考试 -->
        <el-card shadow="never" class="mb16">
          <template #header>
            <div class="card-header">
              <span>待办考试</span>
              <el-button text type="primary" @click="$router.push('/exam')">全部</el-button>
            </div>
          </template>
          <el-empty v-if="!todoExams.length" description="暂无待办考试" :image-size="60" />
          <div v-for="e in todoExams" :key="e.examId" class="exam-item">
            <div class="exam-info">
              <div class="exam-name">{{ e.examName }}</div>
              <div class="text-secondary">{{ e.startTime }} ~ {{ e.endTime }} · {{ e.durationMin }}分钟</div>
            </div>
            <el-button
              v-if="e.canEnter"
              type="primary"
              size="small"
              @click="$router.push(`/exam/notice/${e.examId}`)"
            >
              {{ e.mySheetStatus === 'IN_EXAM' ? '继续答题' : '进入考试' }}
            </el-button>
            <el-tag v-else size="small" type="info">{{ e.cannotEnterReason || '未开始' }}</el-tag>
          </div>
        </el-card>

        <!-- 学习任务 -->
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>学习任务</span>
              <el-button text type="primary" @click="$router.push('/learn/tasks')">全部</el-button>
            </div>
          </template>
          <el-empty v-if="!todoTasks.length" description="暂无学习任务" :image-size="60" />
          <div v-for="t in todoTasks" :key="t.taskId" class="task-item">
            <div>
              <div class="exam-name">{{ t.title }}</div>
              <div class="text-secondary">
                截止 {{ t.deadline }} · 要求时长 {{ fmtDuration(t.requiredSec) }}
              </div>
            </div>
            <el-tag :type="t.myStatus === 'EXPIRED' ? 'danger' : 'warning'" size="small">
              {{ t.myStatus === 'EXPIRED' ? '已逾期' : '待完成' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>

      <el-col :span="14">
        <!-- 最新通知 -->
        <el-card shadow="never" class="notice-card">
          <template #header>
            <div class="card-header">
              <span>最新通知<el-badge v-if="unreadCount" :value="unreadCount" class="badge" /></span>
              <el-button text type="primary" @click="readAll">全部已读</el-button>
            </div>
          </template>
          <el-empty v-if="!notices.length" description="暂无通知" :image-size="60" />
          <div v-for="n in notices" :key="n.noticeId" class="notice-item" @click="openNotice(n)">
            <el-tag size="small" :type="noticeTypeTag(n.noticeType)">{{ noticeTypeName(n.noticeType) }}</el-tag>
            <span class="notice-title" :class="{ unread: !n.read }">{{ n.title }}</span>
            <span class="text-secondary">{{ n.sendTime }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="noticeDialog" :title="currentNotice?.title" width="560px">
      <div class="rich-content" v-html="currentNotice?.content || ''"></div>
      <template #footer>
        <span class="text-secondary">{{ currentNotice?.sendTime }}</span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getPersonalStat, getLearnTasks } from '@/api/learn'
import { getMyExams } from '@/api/take'
import { getNotices, markNoticeRead } from '@/api/notice'
import { fmtDuration } from '@/utils'

const loading = ref(false)
const stat = ref({})
const todoExams = ref([])
const todoTasks = ref([])
const notices = ref([])
const unreadCount = ref(0)
const noticeDialog = ref(false)
const currentNotice = ref(null)

const taskDoneRateText = computed(() => {
  const r = stat.value.taskStats?.doneRate
  return r == null ? '0%' : `${Math.round(r * 100)}%`
})

const NOTICE_TYPES = { EXAM: '考试', SCORE: '成绩', LEARN_TASK: '学习任务', GENERAL: '通知' }
const noticeTypeName = (t) => NOTICE_TYPES[t] || '通知'
const noticeTypeTag = (t) => ({ EXAM: 'primary', SCORE: 'success', LEARN_TASK: 'warning', GENERAL: 'info' }[t] || 'info')

async function load() {
  loading.value = true
  try {
    const [s, exams, tasks, ns] = await Promise.all([
      getPersonalStat().catch(() => ({})),
      getMyExams({ type: 'TODO', pageNum: 1, pageSize: 5 }, { silent: true }).catch(() => ({ list: [] })),
      getLearnTasks({ status: 'TODO', pageNum: 1, pageSize: 5 }).catch(() => ({ list: [] })),
      getNotices({ pageNum: 1, pageSize: 8 }).catch(() => ({ list: [] }))
    ])
    stat.value = s || {}
    todoExams.value = exams.list || []
    todoTasks.value = tasks.list || []
    notices.value = ns.list || []
    unreadCount.value = ns.unreadCount || 0
  } finally {
    loading.value = false
  }
}

async function openNotice(n) {
  currentNotice.value = n
  noticeDialog.value = true
  if (!n.read) {
    await markNoticeRead(n.noticeId).catch(() => {})
    n.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
}

async function readAll() {
  await markNoticeRead(0).catch(() => {})
  notices.value.forEach((n) => (n.read = true))
  unreadCount.value = 0
}

onMounted(load)
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #1f2d3d;
}
.stat-label {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.exam-item,
.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}
.exam-item:last-child,
.task-item:last-child {
  border-bottom: none;
}
.exam-name {
  font-weight: 500;
  margin-bottom: 4px;
}
.notice-card {
  min-height: 480px;
}
.notice-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
  cursor: pointer;
}
.notice-item:last-child {
  border-bottom: none;
}
.notice-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notice-title.unread {
  font-weight: 600;
}
.badge {
  margin-left: 8px;
  vertical-align: top;
}
</style>
