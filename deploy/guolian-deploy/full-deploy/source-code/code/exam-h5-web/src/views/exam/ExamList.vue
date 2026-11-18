<template>
  <div>
    <van-nav-bar title="在线考试" />
    <van-tabs v-model:active="active" sticky @change="onTabChange">
      <van-tab title="待考" name="TODO" />
      <van-tab title="历史" name="HISTORY" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
        <van-empty v-if="!loading && list.length === 0" :description="active === 'TODO' ? '暂无待考考试' : '暂无历史考试'" />
        <van-cell-group v-for="exam in list" :key="exam.examId" inset class="exam-card">
          <div class="exam-head">
            <span class="exam-name">{{ exam.examName }}</span>
            <van-tag v-if="active === 'TODO'" :type="exam.canEnter ? 'success' : 'default'">
              {{ exam.canEnter ? '可进入' : sheetStatusName[exam.mySheetStatus] || '未开始' }}
            </van-tag>
            <!-- 缺考无本人成绩（详情接口按“有已评答卷”判定已发布），不显示“成绩已发布” -->
            <van-tag v-else-if="exam.scorePublished && exam.mySheetStatus !== 'ABSENT'" type="primary">成绩已发布</van-tag>
            <van-tag v-else type="warning">{{ sheetStatusName[exam.mySheetStatus] || '待发布' }}</van-tag>
          </div>
          <div class="text-muted exam-meta">
            <div>考试时间：{{ exam.startTime }} ~ {{ exam.endTime }}</div>
            <div>时长：{{ exam.durationMin }} 分钟</div>
            <div v-if="active === 'TODO' && !exam.canEnter && exam.cannotEnterReason" class="exam-reason">
              {{ exam.cannotEnterReason }}
            </div>
          </div>
          <div class="exam-actions">
            <template v-if="active === 'TODO'">
              <van-button
                v-if="exam.mySheetStatus === 'IN_EXAM'"
                type="primary"
                size="small"
                block
                @click="resumeExam(exam)"
              >
                继续答题
              </van-button>
              <van-button v-else type="primary" size="small" block :disabled="!exam.canEnter" @click="goNotice(exam)">
                进入考试
              </van-button>
            </template>
            <van-button
              v-else-if="exam.scorePublished && exam.mySheetStatus !== 'ABSENT'"
              type="primary"
              plain
              size="small"
              block
              @click="$router.push(`/exam/result/${exam.examId}`)"
            >
              查看成绩
            </van-button>
            <van-button v-else size="small" block disabled>
              {{ exam.mySheetStatus === 'ABSENT' ? '缺考无成绩' : '成绩待发布' }}
            </van-button>
          </div>
        </van-cell-group>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getExams } from '../../api/exam'
import { useExamStore } from '../../stores/exam'
import { sheetStatusName } from '../../utils/format'

const router = useRouter()
const examStore = useExamStore()
const active = ref('TODO')
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

async function fetchList(reset) {
  if (reset) {
    pageNum.value = 1
    list.value = []
    finished.value = false
  }
  const data = await getExams({ type: active.value, pageNum: pageNum.value, pageSize })
  const items = data.list || []
  list.value = reset ? items : list.value.concat(items)
  if (items.length < pageSize || list.value.length >= (data.total || 0)) finished.value = true
  pageNum.value += 1
}

async function onLoad() {
  try {
    await fetchList(false)
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  try {
    await fetchList(true)
  } finally {
    refreshing.value = false
  }
}

function onTabChange() {
  onRefresh()
}

function goNotice(exam) {
  router.push({ path: `/exam/notice/${exam.examId}`, query: { name: exam.examName } })
}

function resumeExam(exam) {
  const s = examStore.loadSession(exam.examId)
  if (s && s.sheetId) {
    router.push(`/exam/answer/${exam.examId}`)
  } else {
    // 本地无会话（换终端）：重新走进入流程，服务端按 RESUME 恢复
    showToast('正在恢复考试会话...')
    goNotice(exam)
  }
}
</script>

<style scoped>
.exam-card {
  margin-top: 12px;
  padding: 12px;
}

.exam-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.exam-name {
  font-size: 15px;
  font-weight: 600;
  color: #323233;
}

.exam-meta {
  margin: 8px 0;
  line-height: 1.8;
}

.exam-reason {
  color: #ee0a24;
}

.exam-actions {
  margin-top: 8px;
}
</style>
