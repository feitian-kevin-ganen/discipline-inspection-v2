<template>
  <div>
    <van-nav-bar title="日常练习">
      <template #right>
        <van-icon name="records-o" size="20" @click="$router.push('/practice/records')" />
      </template>
    </van-nav-bar>

    <van-cell-group inset class="block">
      <van-cell title="练习模式" />
      <div class="mode-row">
        <van-tag
          v-for="m in modes"
          :key="m.key"
          :type="mode === m.key ? 'primary' : 'default'"
          size="large"
          class="mode-tag"
          @click="mode = m.key"
        >
          {{ m.name }}
        </van-tag>
      </div>
    </van-cell-group>

    <van-cell-group v-if="mode === 'CATEGORY'" inset class="block">
      <van-cell title="选择法规分类" />
      <van-list v-model:loading="banksLoading" :finished="true">
        <van-empty v-if="!banksLoading && banks.length === 0" description="暂无可用法规分类" />
        <van-cell
          v-for="b in banks"
          :key="b.bankId"
          clickable
          @click="bankId = b.bankId"
        >
          <template #title>
            <span :class="{ 'bank-active': bankId === b.bankId }">{{ b.bankName }}</span>
          </template>
          <template #value>
            <span class="text-muted">{{ b.quesCount }} 题</span>
            <van-icon v-if="bankId === b.bankId" name="success" color="#1989fa" class="bank-check" />
          </template>
        </van-cell>
      </van-list>
    </van-cell-group>

    <van-cell-group inset class="block">
      <van-cell title="题目数量" />
      <div class="mode-row">
        <van-tag
          v-for="c in counts"
          :key="c"
          :type="count === c ? 'primary' : 'default'"
          size="large"
          class="mode-tag"
          @click="count = c"
        >
          {{ c }} 题
        </van-tag>
      </div>
    </van-cell-group>

    <div class="start-btn">
      <van-button round block type="primary" :loading="starting" @click="onStart">开始练习</van-button>
    </div>

    <van-cell-group inset class="block">
      <van-cell title="练习记录" icon="todo-list-o" is-link to="/practice/records" />
      <van-cell title="练习统计" icon="bar-chart-o" is-link to="/practice/stat" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getPracticeBanks, startPractice } from '../../api/practice'
import { usePracticeStore } from '../../stores/practice'

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
    // 错误已由 request 统一 toast
  } finally {
    banksLoading.value = false
  }
}

async function onStart() {
  if (mode.value === 'CATEGORY' && !bankId.value) {
    showToast('请选择法规分类')
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
    // 错误已由 request 统一 toast（如 70002 题量不足）
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
.block {
  margin-top: 12px;
}

.mode-row {
  display: flex;
  gap: 12px;
  padding: 4px 16px 14px;
}

.mode-tag {
  padding: 6px 18px;
  cursor: pointer;
}

.bank-active {
  color: #1989fa;
  font-weight: 600;
}

.bank-check {
  margin-left: 6px;
  vertical-align: middle;
}

.start-btn {
  margin: 20px 16px 8px;
}
</style>
