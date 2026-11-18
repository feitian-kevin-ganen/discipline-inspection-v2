<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">日常练习</span>
          <div>
            <el-button size="small" @click="$router.push('/practice/records')">练习记录</el-button>
            <el-button size="small" @click="$router.push('/practice/stat')">练习统计</el-button>
          </div>
        </div>
      </template>

      <el-form label-width="90px" class="form">
        <el-form-item label="练习模式">
          <el-radio-group v-model="mode">
            <el-radio-button value="CATEGORY">按法规分类</el-radio-button>
            <el-radio-button value="RANDOM">全库随机</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="mode === 'CATEGORY'" label="法规分类">
          <el-select v-model="bankId" placeholder="请选择法规分类" style="width: 460px" :loading="banksLoading">
            <el-option
              v-for="b in banks"
              :key="b.bankId"
              :label="`${b.bankName}（${b.quesCount} 题）`"
              :value="b.bankId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题目数量">
          <el-radio-group v-model="count">
            <el-radio-button :value="5">5 题</el-radio-button>
            <el-radio-button :value="10">10 题</el-radio-button>
            <el-radio-button :value="20">20 题</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="starting" @click="onStart">开始练习</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="练习为日常刷题，不计入成绩；作答后立即显示正误与解析。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPracticeBanks, startPractice } from '@/api/practice'
import { usePracticeStore } from '@/stores/practice'

const router = useRouter()
const practiceStore = usePracticeStore()

const modes = [
  { key: 'CATEGORY', name: '按法规分类' },
  { key: 'RANDOM', name: '全库随机' }
]
const counts = [5, 10, 20]

const mode = ref('CATEGORY')
const banks = ref([])
const bankId = ref(null)
const count = ref(10)
const banksLoading = ref(false)
const starting = ref(false)

async function loadBanks() {
  banksLoading.value = true
  try {
    banks.value = await getPracticeBanks()
    if (banks.value.length && !bankId.value) bankId.value = banks.value[0].bankId
  } catch (e) {
    // 错误已由 request 拦截器统一提示
  } finally {
    banksLoading.value = false
  }
}

async function onStart() {
  if (mode.value === 'CATEGORY' && !bankId.value) {
    ElMessage.warning('请选择法规分类')
    return
  }
  starting.value = true
  try {
    const body = { mode: mode.value, count: count.value }
    if (mode.value === 'CATEGORY') body.bankId = bankId.value
    const data = await startPractice(body)
    practiceStore.setSession(data, body.bankId)
    router.push('/practice/answer')
  } catch (e) {
    // 错误已由 request 拦截器统一提示（如 70002 题量不足）
  } finally {
    starting.value = false
  }
}

watch(mode, () => {
  if (mode.value === 'CATEGORY' && banks.value.length === 0) loadBanks()
})

onMounted(loadBanks)
</script>

<style scoped>
.page {
  max-width: 860px;
  margin: 0 auto;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 17px;
  font-weight: 600;
}
.form {
  max-width: 640px;
}
</style>
