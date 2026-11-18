<template>
  <div>
    <van-nav-bar title="练习统计" left-arrow @click-left="$router.back()" />

    <van-cell-group inset class="block">
      <div class="stat-grid">
        <div class="stat-item">
          <div class="stat-num">{{ stat.totalQuestions || 0 }}</div>
          <div class="stat-label">总题量</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ pct(stat.accuracy) }}</div>
          <div class="stat-label">正确率</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stat.practiceDays || 0 }}</div>
          <div class="stat-label">练习天数</div>
        </div>
      </div>
    </van-cell-group>

    <van-cell-group inset class="block">
      <van-cell title="按法规正确率" />
      <van-empty v-if="byLaw.length === 0" description="暂无数据" />
      <div v-for="l in byLaw" :key="l.sourceLaw" class="law-row">
        <div class="law-head">
          <span class="law-name">{{ l.sourceLaw }}</span>
          <span class="text-muted">{{ l.questions }} 题 · {{ pct(l.accuracy) }}</span>
        </div>
        <van-progress
          :percentage="Math.round((l.accuracy || 0) * 100)"
          :color="(l.accuracy || 0) >= 0.6 ? '#07c160' : '#ee0a24'"
          stroke-width="6"
        />
      </div>
    </van-cell-group>

    <van-cell-group inset class="block">
      <van-cell title="薄弱知识点" />
      <van-empty v-if="weakPoints.length === 0" description="暂无薄弱知识点" />
      <van-cell v-for="w in weakPoints" :key="w.knowledgePoint" :title="w.knowledgePoint">
        <template #value>
          <span class="weak-text">错 {{ w.wrongCount }} 次 · 错误率 {{ pct(w.wrongRate) }}</span>
        </template>
      </van-cell>
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPracticeStat } from '../../api/practice'

const stat = ref({})
const byLaw = computed(() => stat.value.byLaw || [])
const weakPoints = computed(() => stat.value.weakPoints || [])

function pct(v) {
  return `${Math.round((v || 0) * 100)}%`
}

onMounted(async () => {
  try {
    stat.value = await getPracticeStat()
  } catch (e) {
    // 错误已由 request 统一 toast
  }
})
</script>

<style scoped>
.block {
  margin-top: 12px;
}

.stat-grid {
  display: flex;
  padding: 20px 0;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 24px;
  font-weight: 600;
  color: #b3271e;
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
}

.law-row {
  padding: 8px 16px 14px;
}

.law-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 14px;
}

.law-name {
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.weak-text {
  color: #ee0a24;
  font-size: 13px;
}
</style>
