<template>
  <div class="page page-has-navbar">
    <van-nav-bar title="我的成绩" left-arrow @click-left="$router.back()" fixed placeholder />
    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <van-empty v-if="!loading && list.length === 0" description="暂无已发布的成绩" />
      <van-cell-group
        v-for="item in list"
        :key="item.examId + '-' + (item.retakeSeq || 0)"
        inset
        class="score-card"
        @click="$router.push(`/exam/result/${item.examId}`)"
      >
        <div class="score-head">
          <span class="score-name van-ellipsis">{{ item.examName }}</span>
          <span class="score-value" :class="{ fail: !item.passed }">
            {{ item.myScore }}<span class="score-total">/{{ item.totalScore }}</span>
          </span>
        </div>
        <div class="score-meta">
          <van-tag v-if="item.grade" type="primary" plain>{{ item.grade }}</van-tag>
          <van-tag :type="item.passed ? 'success' : 'danger'" plain>{{ item.passed ? '合格' : '不合格' }}</van-tag>
          <span v-if="item.rankInOrg" class="text-muted">单位排名 {{ item.rankInOrg }}</span>
          <span v-if="item.retakeSeq" class="text-muted">第 {{ item.retakeSeq }} 次补考</span>
        </div>
        <div class="text-muted">测试时间：{{ item.examTime }}</div>
      </van-cell-group>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getMyTranscripts } from '../../api/exam'

const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const data = await getMyTranscripts({ pageNum: pageNum.value, pageSize })
    const items = data.list || []
    list.value = list.value.concat(items)
    if (items.length < pageSize || list.value.length >= (data.total || 0)) finished.value = true
    pageNum.value += 1
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.score-card {
  margin-top: 12px;
  padding: 12px;
}

.score-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  margin-right: 12px;
}

.score-value {
  font-size: 20px;
  font-weight: 700;
  color: #07c160;
}

.score-value.fail {
  color: #ee0a24;
}

.score-total {
  font-size: 12px;
  color: #969799;
  font-weight: 400;
}

.score-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}
</style>
