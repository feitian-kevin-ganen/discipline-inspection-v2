<template>
  <div>
    <van-nav-bar title="练习记录" left-arrow @click-left="$router.back()" />
    <div class="switch-row">
      <span>只看错题</span>
      <van-switch v-model="onlyWrong" size="22" @change="onRefresh" />
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
        <van-empty v-if="!loading && list.length === 0" :description="onlyWrong ? '暂无错题' : '暂无练习记录'" />
        <van-cell-group v-for="r in list" :key="r.recordId" inset class="record-card">
          <div class="record-head">
            <van-tag :type="r.isRight ? 'success' : 'danger'">{{ r.isRight ? '正确' : '错误' }}</van-tag>
            <span class="text-muted record-time">{{ formatTime(r.answerTime) }}</span>
          </div>
          <div class="record-stem">{{ r.stemText }}</div>
          <div class="record-meta text-muted">
            <div>我的答案：{{ answerText(r.myAnswer) }}</div>
            <div v-if="r.sourceLaw">来源：{{ r.sourceLaw }}</div>
          </div>
          <template v-if="onlyWrong && r.analysis !== undefined">
            <div class="record-detail">
              <div>正确答案：{{ answerText(r.correctAnswer) }}</div>
              <div v-if="r.analysis" class="record-analysis">解析：{{ r.analysis }}</div>
            </div>
          </template>
        </van-cell-group>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getPracticeRecords } from '../../api/practice'
import { parseServerTime } from '../../utils/format'

const onlyWrong = ref(false)
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

function answerText(ans) {
  if (!ans) return '-'
  const keys = ans.keys || ans.selected
  return Array.isArray(keys) ? keys.join('、') : String(keys || '-')
}

function formatTime(str) {
  const t = parseServerTime(str)
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function fetchList(reset) {
  if (reset) {
    pageNum.value = 1
    list.value = []
    finished.value = false
  }
  const params = { pageNum: pageNum.value, pageSize }
  if (onlyWrong.value) params.onlyWrong = true
  const data = await getPracticeRecords(params)
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
</script>

<style scoped>
.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  font-size: 14px;
  color: #323233;
  background: #fff;
}

.record-card {
  margin-top: 12px;
  padding: 12px;
}

.record-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.record-stem {
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.6;
  color: #323233;
}

.record-meta {
  margin-top: 8px;
  line-height: 1.8;
}

.record-detail {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebedf0;
  font-size: 14px;
  line-height: 1.8;
  color: #323233;
}

.record-analysis {
  color: #646566;
}
</style>
