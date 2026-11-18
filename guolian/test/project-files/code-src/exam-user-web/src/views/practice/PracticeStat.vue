<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">练习统计</span>
          <el-button size="small" @click="$router.push('/practice')">返回练习</el-button>
        </div>
      </template>

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

      <el-divider content-position="left">按法规正确率</el-divider>
      <el-empty v-if="byLaw.length === 0" description="暂无数据" :image-size="80" />
      <div v-for="l in byLaw" :key="l.sourceLaw" class="law-row">
        <div class="law-head">
          <span class="law-name" :title="l.sourceLaw">{{ l.sourceLaw }}</span>
          <span class="text-secondary">{{ l.questions }} 题 · {{ pct(l.accuracy) }}</span>
        </div>
        <el-progress
          :percentage="Math.round((l.accuracy || 0) * 100)"
          :color="(l.accuracy || 0) >= 0.6 ? '#67c23a' : '#f56c6c'"
          :stroke-width="10"
        />
      </div>

      <el-divider content-position="left">薄弱知识点</el-divider>
      <el-empty v-if="weakPoints.length === 0" description="暂无薄弱知识点" :image-size="80" />
      <el-table v-else :data="weakPoints" size="default">
        <el-table-column prop="knowledgePoint" label="知识点" min-width="220" show-overflow-tooltip />
        <el-table-column prop="wrongCount" label="错误次数" width="110" align="center" />
        <el-table-column label="错误率" width="180" align="center">
          <template #default="{ row }">
            <span class="weak-text">{{ pct(row.wrongRate) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPracticeStat } from '@/api/practice'

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
    // 错误已由 request 拦截器统一提示
  }
})
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
.stat-grid {
  display: flex;
  padding: 12px 0 4px;
}
.stat-item {
  flex: 1;
  text-align: center;
}
.stat-num {
  font-size: 28px;
  font-weight: 600;
  color: #b3271e;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
.law-row {
  padding: 6px 0 14px;
}
.law-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 14px;
}
.law-name {
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}
.weak-text {
  color: #f56c6c;
}
</style>
