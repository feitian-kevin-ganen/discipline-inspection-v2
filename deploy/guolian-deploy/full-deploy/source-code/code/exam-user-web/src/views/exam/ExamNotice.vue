<template>
  <div class="page notice-page" v-loading="loading">
    <el-card shadow="never" class="notice-card" v-if="exam">
      <template #header>
        <span class="card-title">考试须知</span>
      </template>
      <h3 class="exam-name">{{ exam.examName }}</h3>
      <el-descriptions :column="2" border class="mb16">
        <el-descriptions-item label="考试时间窗口">{{ exam.startTime }} ~ {{ exam.endTime }}</el-descriptions-item>
        <el-descriptions-item label="考试时长">{{ exam.durationMin }} 分钟</el-descriptions-item>
        <el-descriptions-item label="答卷状态">
          {{ { NOT_ENTERED: '未进入', IN_EXAM: '答题中（可继续）' }[exam.mySheetStatus] || exam.mySheetStatus }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="rules">
        <h4>请仔细阅读以下考试纪律：</h4>
        <ol>
          <li>请在考试时间窗口内进入考试；进入后开始计时，到点系统自动交卷。</li>
          <li>答题过程中答案将自动保存，断网或意外关闭后可重新进入继续作答，考试时长不因断线延长。</li>
          <li>考试期间请勿切换页面或离开考试窗口，切屏次数超过上限将被强制交卷。</li>
          <li>同一账号同一时间仅允许一个终端在线答题，异地登录将导致当前终端被强制下线。</li>
          <li>交卷前请检查是否有未作答题目；交卷后不可再次进入本场考试。</li>
          <li>请独立完成考试，严禁抄袭、查阅资料等违规行为。</li>
        </ol>
        <div v-if="exam.notice" class="rich-content server-notice" v-html="exam.notice"></div>
      </div>

      <el-checkbox v-model="confirmed">我已阅读并同意遵守上述考试须知</el-checkbox>

      <div class="actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" size="large" :disabled="!confirmed" :loading="entering" @click="onEnter">
          {{ exam.mySheetStatus === 'IN_EXAM' ? '继续答题' : '确认并进入考试' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyExams, enterExam } from '@/api/take'
import { getDeviceId } from '@/utils'

const route = useRoute()
const router = useRouter()
const examId = route.params.examId

const loading = ref(true)
const entering = ref(false)
const confirmed = ref(false)
const exam = ref(null)

async function load() {
  loading.value = true
  try {
    // 考生端无单场详情接口，从待考列表中定位本场次
    const res = await getMyExams({ type: 'TODO', pageNum: 1, pageSize: 100 })
    exam.value = (res.list || []).find((e) => String(e.examId) === String(examId)) || null
    if (!exam.value) {
      ElMessage.warning('未找到该考试或不在待考列表中')
      router.replace('/exam')
    }
  } finally {
    loading.value = false
  }
}

async function onEnter() {
  entering.value = true
  try {
    const data = await enterExam(examId, {
      noticeConfirmed: true,
      deviceId: getDeviceId(),
      terminal: 'PC_APP'
    })
    sessionStorage.setItem(`exam_enter_${examId}`, JSON.stringify(data))
    sessionStorage.setItem(`exam_sheet_${examId}`, String(data.sheetId))
    router.replace(`/exam/take/${examId}?sheetId=${data.sheetId}`)
  } catch (err) {
    if (err && err.code === 50003) {
      ElMessage.info('您已交卷，不可重复进入')
      router.replace('/exam')
    }
    // 其余错误（50001/50002/50005/50008 等）由拦截器统一提示
  } finally {
    entering.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notice-page {
  max-width: 860px;
  margin: 0 auto;
}
.card-title {
  font-weight: 600;
}
.exam-name {
  margin: 0 0 16px;
  text-align: center;
}
.rules {
  margin: 16px 0;
}
.rules ol {
  line-height: 2;
  color: #606266;
  padding-left: 20px;
}
.server-notice {
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  margin-top: 12px;
  background: #fafafa;
}
.actions {
  margin-top: 24px;
  text-align: center;
}
</style>
