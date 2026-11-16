<template>
  <div class="page learn-center">
    <el-row :gutter="16">
      <el-col :span="5">
        <el-card shadow="never">
          <template #header><span class="card-title">资料分类</span></template>
          <el-tree
            :data="categoryTree"
            :props="{ label: 'label' }"
            node-key="key"
            highlight-current
            default-expand-all
            :current-node-key="activeCategory"
            @node-click="onCategoryClick"
          />
        </el-card>
      </el-col>
      <el-col :span="19">
        <el-card shadow="never">
          <template #header>
            <div class="toolbar">
              <el-input
                v-model="keyword"
                placeholder="搜索资料标题"
                clearable
                style="width: 240px"
                :prefix-icon="Search"
                @input="onFilterChange"
              />
              <el-select v-model="activeTag" placeholder="按标签筛选" clearable style="width: 180px" @change="onFilterChange">
                <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
              </el-select>
              <el-radio-group v-model="activeType" @change="onFilterChange">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="DOC">文档</el-radio-button>
                <el-radio-button value="VIDEO">视频</el-radio-button>
                <el-radio-button value="RICHTEXT">图文</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-loading="loading">
            <el-empty v-if="!pagedList.length" description="暂无学习资料" />
            <el-row :gutter="16">
              <el-col v-for="m in pagedList" :key="m.materialId" :span="8">
                <el-card shadow="hover" class="material-card" @click="goDetail(m)">
                  <div
                    class="cover"
                    :class="m.coverUrl ? '' : 'cover-' + (m.materialType || 'RICHTEXT').toLowerCase()"
                    :style="m.coverUrl ? { backgroundImage: `url(${coverSrc(m)})` } : {}"
                  >
                    <el-icon v-if="!m.coverUrl" :size="40">
                      <VideoPlay v-if="m.materialType === 'VIDEO'" />
                      <Document v-else-if="m.materialType === 'DOC'" />
                      <Reading v-else />
                    </el-icon>
                    <el-tag class="type-tag" size="small" :type="typeTag(m.materialType)">
                      {{ MATERIAL_TYPE_NAME[m.materialType] || m.materialType }}
                    </el-tag>
                  </div>
                  <div class="m-title" :title="m.title">{{ m.title }}</div>
                  <div class="m-meta text-secondary">
                    <span>{{ m.catName }}</span>
                    <span v-if="m.durationSec">时长 {{ fmtDuration(m.durationSec) }}</span>
                  </div>
                  <div class="m-tags" v-if="m.tags && m.tags.length">
                    <el-tag v-for="t in m.tags.slice(0, 3)" :key="t" size="small" effect="plain">{{ t }}</el-tag>
                  </div>
                  <div class="m-progress">
                    <el-progress
                      :percentage="Math.round(m.myProgress?.progressPct ?? 0)"
                      :status="m.myProgress?.status === 'COMPLETED' ? 'success' : undefined"
                      :stroke-width="8"
                    />
                    <span class="status-text">{{ statusText(m.myProgress?.status) }}</span>
                  </div>
                </el-card>
              </el-col>
            </el-row>
            <div class="pager" v-if="filteredList.length > pageSize">
              <el-pagination
                v-model:current-page="pageNum"
                :page-size="pageSize"
                :total="filteredList.length"
                layout="prev, pager, next, total"
                background
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { getMaterials } from '@/api/learn'
import { fmtDuration, MATERIAL_TYPE_NAME } from '@/utils'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
// 封面图走同源代理接口，CSS background 无法带请求头，用 access_token 查询参数鉴权
function coverSrc(m) {
  return `${m.coverUrl}?access_token=${encodeURIComponent(userStore.accessToken)}`
}

const router = useRouter()
const loading = ref(false)
const materials = ref([])
const keyword = ref('')
const activeTag = ref('')
const activeType = ref('')
const activeCategory = ref('__ALL__')
const pageNum = ref(1)
const pageSize = 12

const categoryTree = computed(() => {
  const cats = [...new Set(materials.value.map((m) => m.catName).filter(Boolean))]
  return [
    { key: '__ALL__', label: `全部资料（${materials.value.length}）` },
    ...cats.map((c) => ({
      key: c,
      label: `${c}（${materials.value.filter((m) => m.catName === c).length}）`
    }))
  ]
})

const allTags = computed(() => {
  const set = new Set()
  materials.value.forEach((m) => (m.tags || []).forEach((t) => set.add(t)))
  return [...set]
})

const filteredList = computed(() => {
  const kw = keyword.value.trim()
  return materials.value.filter((m) => {
    if (activeCategory.value !== '__ALL__' && m.catName !== activeCategory.value) return false
    if (activeType.value && m.materialType !== activeType.value) return false
    if (activeTag.value && !(m.tags || []).includes(activeTag.value)) return false
    if (kw && !m.title.includes(kw)) return false
    return true
  })
})

const pagedList = computed(() => {
  const start = (pageNum.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

function typeTag(t) {
  return { DOC: 'primary', VIDEO: 'danger', RICHTEXT: 'success' }[t] || 'info'
}
function statusText(s) {
  return { NOT_STARTED: '未开始', IN_PROGRESS: '学习中', COMPLETED: '已完成' }[s] || '未开始'
}
function onCategoryClick(node) {
  activeCategory.value = node.key
  pageNum.value = 1
}
function onFilterChange() {
  pageNum.value = 1
}
function goDetail(m) {
  router.push(`/learn/material/${m.materialId}`)
}

async function loadAll() {
  loading.value = true
  try {
    const size = 200
    let page = 1
    let all = []
    let total = 0
    do {
      const res = await getMaterials({ pageNum: page, pageSize: size, sortBy: 'createdTime', sortOrder: 'desc' })
      all = all.concat(res.list || [])
      total = res.total || 0
      page += 1
    } while (all.length < total && page <= 20)
    materials.value = all
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.material-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.cover {
  height: 120px;
  background: #f0f2f5 center/cover no-repeat;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin-bottom: 10px;
}
/* 无封面时按资料类型区分底色与图标色：文档-蓝、视频-红、图文-绿 */
.cover-doc {
  background: linear-gradient(135deg, #dbe9fd 0%, #eff5ff 100%);
}
.cover-doc .el-icon {
  color: #3b76d1;
}
.cover-video {
  background: linear-gradient(135deg, #fde2e2 0%, #fff1ec 100%);
}
.cover-video .el-icon {
  color: #d9553f;
}
.cover-richtext {
  background: linear-gradient(135deg, #ddf2e4 0%, #f1faf4 100%);
}
.cover-richtext .el-icon {
  color: #3d9e63;
}
.type-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}
.m-title {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}
.m-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}
.m-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.m-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-progress .el-progress {
  flex: 1;
}
.status-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}
</style>
