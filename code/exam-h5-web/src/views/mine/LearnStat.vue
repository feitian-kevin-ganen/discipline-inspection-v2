<template>
  <div class="page page-has-navbar">
    <van-nav-bar title="学习统计" left-arrow @click-left="$router.back()" fixed placeholder />
    <van-loading v-if="loading" class="page-loading" size="24px">加载中...</van-loading>

    <template v-else-if="stat">
      <div class="stat-panel">
        <div class="stat-main">{{ formatDuration(stat.totalEffectiveSec || 0) }}</div>
        <div class="text-muted">累计有效学习时长</div>
      </div>

      <van-grid :column-num="3" :border="false" class="stat-grid">
        <van-grid-item>
          <div class="grid-num">{{ ms.completed || 0 }}</div>
          <div class="text-muted">已完成资料</div>
        </van-grid-item>
        <van-grid-item>
          <div class="grid-num">{{ ms.inProgress || 0 }}</div>
          <div class="text-muted">学习中</div>
        </van-grid-item>
        <van-grid-item>
          <div class="grid-num">{{ ms.notStarted || 0 }}</div>
          <div class="text-muted">未开始</div>
        </van-grid-item>
      </van-grid>

      <van-cell-group inset title="学习任务" class="stat-group">
        <van-cell title="任务总数" :value="String(ts.total || 0)" />
        <van-cell title="已完成" :value="String(ts.done || 0)" />
        <van-cell title="进行中" :value="String(ts.doing || 0)" />
        <van-cell title="已逾期" :value="String(ts.expired || 0)" />
        <van-cell title="完成率">
          <template #value>
            <span class="rate-text">{{ Math.round((ts.doneRate || 0) * 100) }}%</span>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group v-if="(stat.categoryDist || []).length" inset title="分类学习分布" class="stat-group">
        <div v-for="c in stat.categoryDist" :key="c.catId" class="cat-item">
          <div class="cat-head">
            <span>{{ c.catName }}</span>
            <span class="text-muted">{{ formatDuration(c.effectiveSec || 0) }} · 完成 {{ c.completedCount || 0 }} 项</span>
          </div>
          <van-progress :percentage="catPct(c)" stroke-width="6" />
        </div>
      </van-cell-group>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getPersonalStat } from '../../api/learn'
import { formatDuration } from '../../utils/format'

const loading = ref(true)
const stat = ref(null)
const ms = computed(() => (stat.value && stat.value.materialStats) || {})
const ts = computed(() => (stat.value && stat.value.taskStats) || {})

const maxCatSec = computed(() => {
  const list = (stat.value && stat.value.categoryDist) || []
  return Math.max(1, ...list.map((c) => c.effectiveSec || 0))
})

function catPct(c) {
  return Math.round(((c.effectiveSec || 0) / maxCatSec.value) * 100)
}

onMounted(async () => {
  try {
    stat.value = await getPersonalStat()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-loading {
  display: block;
  text-align: center;
  margin-top: 80px;
}

.stat-panel {
  text-align: center;
  padding: 28px 16px;
  background: #fff;
}

.stat-main {
  font-size: 28px;
  font-weight: 700;
  color: #1989fa;
  margin-bottom: 6px;
}

.stat-grid {
  background: #fff;
  margin-bottom: 12px;
  padding-bottom: 12px;
}

.grid-num {
  font-size: 22px;
  font-weight: 600;
  color: #323233;
}

.stat-group {
  margin-top: 12px;
}

.rate-text {
  color: #07c160;
  font-weight: 600;
}

.cat-item {
  padding: 10px 16px;
}

.cat-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #323233;
  margin-bottom: 6px;
}
</style>
