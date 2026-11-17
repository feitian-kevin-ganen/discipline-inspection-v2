<template>
  <div class="page result-page">
    <el-card shadow="never" class="result-card">
      <el-result icon="success" title="交卷成功" :sub-title="subTitle">
        <template #extra>
          <div class="score-box" v-if="scoreInfo">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="我的得分">
                <span class="score">{{ scoreInfo.myScore }}</span> / {{ scoreInfo.totalScore }}
              </el-descriptions-item>
              <el-descriptions-item label="等级">
                <el-tag :type="scoreInfo.passed ? 'success' : 'danger'">{{ scoreInfo.grade || '—' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="是否通过">
                {{ scoreInfo.passed ? '通过' : '未通过' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="btns">
            <el-button v-if="scoreVisible" type="primary" @click="$router.push(`/score/${examId}`)">查看成绩明细</el-button>
            <el-button v-else type="primary" plain :loading="checking" @click="checkScore">刷新成绩状态</el-button>
            <el-button @click="$router.replace('/exam')">返回考试中心</el-button>
            <el-button @click="$router.replace('/home')">回到首页</el-button>
          </div>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamResult } from '@/api/take'

const route = useRoute()
const router = useRouter()
const examId = route.params.examId

const submitInfo = ref(null)
const scoreInfo = ref(null)
const checking = ref(false)

const scoreVisible = computed(() => !!scoreInfo.value?.scorePublished || !!submitInfo.value?.scoreVisible)
const subTitle = computed(() => {
  if (scoreInfo.value?.scorePublished) return '成绩已发布'
  if (submitInfo.value?.message) return submitInfo.value.message
  if (submitInfo.value?.submitTime) return `交卷时间：${submitInfo.value.submitTime}，成绩发布后可查询`
  return '成绩发布后可查询'
})

async function checkScore() {
  checking.value = true
  try {
    const res = await getExamResult(examId)
    if (res && res.scorePublished) {
      scoreInfo.value = res
    } else {
      ElMessage.info('成绩尚未发布，请耐心等待')
    }
  } catch (e) {
    // 成绩未发布（50009）属正常业务状态，友好提示而非报错
    if (e && e.code === 50009) {
      ElMessage.info('成绩尚未发布，请耐心等待')
    } else {
      ElMessage.warning('成绩查询失败，请稍后再试')
    }
  } finally {
    checking.value = false
  }
}

onMounted(() => {
  try {
    submitInfo.value = JSON.parse(sessionStorage.getItem(`exam_submit_${examId}`) || 'null')
  } catch (e) {
    submitInfo.value = null
  }
  if (submitInfo.value?.scoreVisible) {
    checkScore()
  }
})
</script>

<style scoped>
.result-page {
  max-width: 860px;
  margin: 0 auto;
}
.score-box {
  margin-bottom: 20px;
}
.score {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}
.btns {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
