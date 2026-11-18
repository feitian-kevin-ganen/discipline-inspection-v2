<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">练习记录</span>
          <el-radio-group v-model="onlyWrong" @change="onRefresh">
            <el-radio-button :value="false">全部</el-radio-button>
            <el-radio-button :value="true">只看错题</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0" :description="onlyWrong ? '暂无错题' : '暂无练习记录'" />

      <div v-for="r in list" :key="r.recordId" class="record-card">
        <div class="record-head">
          <el-tag :type="r.isRight ? 'success' : 'danger'" size="small">{{ r.isRight ? '正确' : '错误' }}</el-tag>
          <span class="text-secondary record-time">{{ formatTime(r.answerTime) }}</span>
        </div>
        <div class="record-stem">{{ r.stemText }}</div>
        <div class="record-meta text-secondary">
          <div>我的答案：{{ answerText(r.myAnswer) }}</div>
          <div v-if="r.sourceLaw">来源：{{ r.sourceLaw }}</div>
        </div>
        <template v-if="onlyWrong">
          <div class="record-detail">
            <div>正确答案：{{ answerText(r.correctAnswer) }}</div>
            <div v-if="r.analysis" class="record-analysis">解析：{{ r.analysis }}</div>
          </div>
        </template>
      </div>

      <div class="pager">
        <el-pagination
          v-if="total > 0"
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="onPage"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPracticeRecords } from '@/api/practice'

const onlyWrong = ref(false)
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)

function answerText(ans) {
  if (!ans) return '-'
  const keys = ans.keys || ans.selected
  return Array.isArray(keys) ? keys.join('、') : String(keys || '-')
}

function formatTime(str) {
  if (!str) return ''
  // 服务端时间为 yyyy-MM-dd HH:mm:ss（本地时区），直接裁剪到分钟
  return String(str).slice(0, 16)
}

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (onlyWrong.value) params.onlyWrong = true
    const data = await getPracticeRecords(params)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    // 错误已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function onRefresh() {
  pageNum.value = 1
  fetchList()
}

function onPage() {
  fetchList()
}

onMounted(fetchList)
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
.record-card {
  padding: 14px 4px;
  border-bottom: 1px solid #ebeef5;
}
.record-card:last-of-type {
  border-bottom: none;
}
.record-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.record-time {
  font-size: 13px;
}
.record-stem {
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.7;
  color: #303133;
}
.record-meta {
  margin-top: 8px;
  line-height: 1.9;
  font-size: 14px;
}
.record-detail {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
}
.record-analysis {
  color: #606266;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
