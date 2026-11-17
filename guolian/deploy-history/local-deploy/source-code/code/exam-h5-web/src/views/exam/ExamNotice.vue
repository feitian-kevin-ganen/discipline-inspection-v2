<template>
  <div class="page page-has-navbar">
    <van-nav-bar title="考试须知" left-arrow @click-left="$router.back()" fixed placeholder />
    <div class="notice-body">
      <div class="exam-title">{{ examName }}</div>
      <van-cell-group inset title="请仔细阅读以下考试须知">
        <div class="notice-text">
          <p>1. 请在考试时间窗口内进入考试，迟到超过规定时限将无法进入。</p>
          <p>2. 考试期间倒计时以服务端时间为准，到时系统将自动收卷。</p>
          <p>3. 作答内容实时自动保存，断网时将保存在本机并在网络恢复后自动同步。</p>
          <p>4. 考试期间请勿切换页面或切出应用，系统会记录切屏次数，超限将被强制交卷。</p>
          <p>5. 同一账号同一时间仅允许一个终端在线答题，其他终端登录将导致本终端被踢下线。</p>
          <p>6. 交卷前请确认作答情况，交卷后不可修改。</p>
        </div>
      </van-cell-group>
      <van-checkbox v-model="confirmed" class="notice-check" icon-size="18px">
        我已阅读并同意遵守考试须知
      </van-checkbox>
      <div class="notice-btn">
        <van-button round block type="primary" :disabled="!confirmed" :loading="entering" @click="onEnter">
          开始考试
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { enterExam } from '../../api/exam'
import { useExamStore } from '../../stores/exam'
import { getDeviceId } from '../../utils/device'

const route = useRoute()
const router = useRouter()
const examStore = useExamStore()
const examId = route.params.examId
const examName = ref(route.query.name || '在线考试')
const confirmed = ref(false)
const entering = ref(false)

async function onEnter() {
  entering.value = true
  try {
    const data = await enterExam(examId, {
      noticeConfirmed: true,
      deviceId: getDeviceId(),
      terminal: 'H5'
    })
    examStore.setSession(examId, data, examName.value)
    if (data.resumeFrom === 'RESUME') {
      showToast('已恢复之前的考试进度')
    }
    router.replace(`/exam/answer/${examId}`)
  } catch (e) {
    // 错误提示由拦截器处理（50001/50002/50003/50005/50008）
  } finally {
    entering.value = false
  }
}
</script>

<style scoped>
.notice-body {
  padding-top: 16px;
}

.exam-title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 16px;
  padding: 0 16px;
}

.notice-text {
  padding: 12px 16px;
  font-size: 14px;
  color: #646566;
  line-height: 1.9;
}

.notice-text p {
  margin: 4px 0;
}

.notice-check {
  margin: 16px;
}

.notice-btn {
  margin: 8px 16px 32px;
}
</style>
