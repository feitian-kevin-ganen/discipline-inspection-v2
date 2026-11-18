<template>
  <div>
    <van-nav-bar title="在线学习">
      <template #right>
        <van-badge :content="todoCount || ''" :show-zero="false">
          <van-icon name="todo-list-o" size="20" @click="$router.push('/learn/tasks')" />
        </van-badge>
      </template>
    </van-nav-bar>

    <van-search v-model="keyword" placeholder="搜索资料" @search="onSearch" @clear="onSearch" />

    <van-tabs v-model:active="activeCat" sticky offset-top="0" @change="onCatChange">
      <van-tab v-for="c in categories" :key="c.key" :title="c.name" :name="c.key" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onSearch">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-empty v-if="!loading && list.length === 0" description="暂无可见学习资料" />
        <van-card
          v-for="item in list"
          :key="item.materialId"
          :title="item.title"
          :desc="item.catName"
          :thumb="coverSrc(item)"
          @click="goDetail(item)"
        >
          <template #tags>
            <div class="card-tags">
              <van-tag plain type="primary">{{ materialTypeName[item.materialType] || item.materialType }}</van-tag>
              <van-tag
                plain
                :color="learnStatusColor[(item.myProgress && item.myProgress.status) || 'NOT_STARTED']"
              >
                {{ learnStatusName[(item.myProgress && item.myProgress.status) || 'NOT_STARTED'] }}
              </van-tag>
              <span v-for="t in item.tags || []" :key="t" class="text-muted tag-item">#{{ t }}</span>
            </div>
            <van-progress
              v-if="item.myProgress && item.myProgress.progressPct > 0"
              :percentage="Math.round(item.myProgress.progressPct * 100)"
              stroke-width="4"
              class="card-progress"
            />
          </template>
          <template #footer>
            <span class="text-muted">时长 {{ formatDuration(item.durationSec) }} · 学习 {{ item.studyCount }} 人次</span>
          </template>
        </van-card>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMaterials, getLearnTasks } from '../../api/learn'
import { materialTypeName, learnStatusName, learnStatusColor, formatDuration } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
// 封面图走同源代理接口，<img> 无法带请求头，用 access_token 查询参数鉴权
function coverSrc(item) {
  return item.coverUrl ? `${item.coverUrl}?access_token=${encodeURIComponent(authStore.accessToken)}` : undefined
}

const router = useRouter()
const keyword = ref('')
const activeCat = ref('ALL')
const categories = ref([{ key: 'ALL', name: '全部' }])
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const todoCount = ref(0)

// 考生端无分类树接口（LIB-01 为管理端），分类由资料列表聚合得出
async function loadCategories() {
  try {
    const data = await getMaterials({ pageNum: 1, pageSize: 200 })
    const seen = new Map()
    ;(data.list || []).forEach((m) => {
      if (m.catName && !seen.has(m.catName)) seen.set(m.catName, m.catId)
    })
    const cats = [{ key: 'ALL', name: '全部' }]
    seen.forEach((id, name) => cats.push({ key: name, name, catId: id }))
    categories.value = cats
  } catch (e) {
    // 分类加载失败不阻塞列表
  }
}

async function fetchList(reset) {
  if (reset) {
    pageNum.value = 1
    list.value = []
    finished.value = false
  }
  const params = { pageNum: pageNum.value, pageSize }
  if (keyword.value) params.keyword = keyword.value
  const cat = categories.value.find((c) => c.key === activeCat.value)
  if (cat && cat.catId) params.catId = cat.catId
  const data = await getMaterials(params)
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

async function onSearch() {
  refreshing.value = true
  try {
    await fetchList(true)
  } finally {
    refreshing.value = false
  }
}

function onCatChange() {
  loading.value = false
  finished.value = false
  list.value = []
  pageNum.value = 1
  onSearch()
}

function goDetail(item) {
  router.push(`/learn/material/${item.materialId}`)
}

async function loadTodoCount() {
  try {
    const data = await getLearnTasks({ status: 'TODO', pageNum: 1, pageSize: 1 })
    todoCount.value = data.total || 0
  } catch (e) {
    // 忽略
  }
}

onMounted(() => {
  loadCategories()
  loadTodoCount()
})
</script>

<style scoped>
.card-tags {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tag-item {
  margin-right: 4px;
}

.card-progress {
  margin-top: 8px;
}
</style>
