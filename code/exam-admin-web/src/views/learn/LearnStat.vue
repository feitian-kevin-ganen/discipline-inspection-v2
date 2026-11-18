<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-radio-group v-model="periodType" @change="onPeriodChange">
          <el-radio-button v-for="p in PERIOD_TYPES" :key="p.value" :value="p.value">{{ p.label }}</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="periodType === 'CUSTOM'"
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="onPeriodChange"
        />
        <div class="spacer"></div>
        <el-dropdown @command="handleExport">
          <el-button type="primary">
            导出<el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="ORG_SUMMARY">单位汇总</el-dropdown-item>
              <el-dropdown-item command="USER_DETAIL">人员明细</el-dropdown-item>
              <el-dropdown-item command="BELOW_STANDARD">未达标名单</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 单位汇总 -->
        <el-tab-pane label="单位汇总" name="org">
          <el-breadcrumb v-if="crumbs.length > 1" style="margin-bottom: 12px">
            <el-breadcrumb-item v-for="(c, i) in crumbs" :key="i">
              <el-button v-if="i < crumbs.length - 1" link type="primary" @click="gotoCrumb(i)">{{ c }}</el-button>
              <span v-else>{{ c }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>

          <!-- 单位层级 -->
          <el-table v-if="!currentLevel || currentLevel.type === 'ORG'" v-loading="orgLoading" :data="orgData.children || []" border>
            <el-table-column prop="orgName" label="单位名称" min-width="200" />
            <el-table-column prop="userCount" label="人数" width="90" align="right" />
            <el-table-column label="人均有效时长" width="130" align="right">
              <template #default="{ row }">{{ formatSec(row.avgEffectiveSec) }}</template>
            </el-table-column>
            <el-table-column label="资料完成率" width="110" align="right">
              <template #default="{ row }">{{ pct(row.materialDoneRate) }}</template>
            </el-table-column>
            <el-table-column label="任务完成率" width="110" align="right">
              <template #default="{ row }">{{ pct(row.taskDoneRate) }}</template>
            </el-table-column>
            <el-table-column prop="belowCount" label="未达标人数" width="110" align="right">
              <template #default="{ row }">
                <span :style="row.belowCount > 0 ? 'color: #f56c6c' : ''">{{ row.belowCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="orgData.hasSubLevel" link type="primary" @click="drillSub(row)">下钻</el-button>
                <el-button link type="primary" @click="drillUser(row)">人员明细</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 人员明细层级 -->
          <template v-else>
            <el-table v-loading="userLoading" :data="userList" border>
              <el-table-column prop="name" label="姓名" width="120" />
              <el-table-column prop="jobNo" label="工号" width="120" />
              <el-table-column label="有效时长" width="130" align="right">
                <template #default="{ row }">{{ formatSec(row.effectiveSec) }}</template>
              </el-table-column>
              <el-table-column label="已完成资料" width="110" align="right">
                <template #default="{ row }">
                  <el-tooltip v-if="row.completedCount > 0" placement="left" :show-after="150">
                    <template #content>
                      <div v-if="!matTips['u' + row.userId]" class="mat-tip-loading">加载中…</div>
                      <div v-else class="mat-tip-list">
                        <div v-for="(t, i) in matTips['u' + row.userId]" :key="i">{{ i + 1 }}. {{ t }}</div>
                      </div>
                    </template>
                    <span class="mat-count" @mouseenter="ensureMatTip('u' + row.userId, { userId: row.userId })">{{ row.completedCount }}</span>
                  </el-tooltip>
                  <span v-else>{{ row.completedCount }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="inProgressCount" label="学习中资料" width="110" align="right" />
              <el-table-column label="任务完成率" width="110" align="right">
                <template #default="{ row }">{{ pct(row.taskDoneRate) }}</template>
              </el-table-column>
              <el-table-column label="达标状态" width="110" align="center">
                <template #default="{ row }">
                  <span v-if="row.isBelowStandard" style="color: #f56c6c; font-weight: 600">未达标</span>
                  <el-tag v-else type="success" size="small">已达标</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                v-model:current-page="userQuery.pageNum"
                v-model:page-size="userQuery.pageSize"
                :total="userTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @size-change="loadUsers"
                @current-change="loadUsers"
              />
            </div>
          </template>
        </el-tab-pane>

        <!-- 学习排行 -->
        <el-tab-pane label="学习排行" name="rank">
          <div class="toolbar">
            <el-radio-group v-model="rankQuery.rankType" @change="loadRank">
              <el-radio-button value="ORG">单位排行</el-radio-button>
              <el-radio-button value="USER">个人排行</el-radio-button>
            </el-radio-group>
            <span>Top</span>
            <el-input-number v-model="rankQuery.topN" :min="1" :max="100" @change="loadRank" />
          </div>
          <div ref="chartRef" style="height: 320px; margin-bottom: 16px"></div>
          <el-table v-loading="rankLoading" :data="rankList" border>
            <el-table-column prop="rank" label="名次" width="80" align="center" />
            <el-table-column :label="rankQuery.rankType === 'ORG' ? '单位名称' : '姓名'" min-width="160">
              <template #default="{ row }">{{ rankQuery.rankType === 'ORG' ? row.orgName : row.name }}</template>
            </el-table-column>
            <el-table-column v-if="rankQuery.rankType === 'USER'" prop="orgName" label="所属单位" min-width="160" show-overflow-tooltip />
            <el-table-column label="有效学习时长" width="150" align="right">
              <template #default="{ row }">{{ formatSec(row.effectiveSec) }}</template>
            </el-table-column>
            <el-table-column label="完成资料数" width="110" align="right">
              <template #default="{ row }">
                <el-tooltip v-if="row.completedCount > 0" placement="left" :show-after="150">
                  <template #content>
                    <div v-if="!matTips[rankTipKey(row)]" class="mat-tip-loading">加载中…</div>
                    <div v-else class="mat-tip-list">
                      <div v-for="(t, i) in matTips[rankTipKey(row)]" :key="i">{{ i + 1 }}. {{ t }}</div>
                    </div>
                  </template>
                  <span class="mat-count" @mouseenter="ensureMatTip(rankTipKey(row), rankTipParams(row))">{{ row.completedCount }}</span>
                </el-tooltip>
                <span v-else>{{ row.completedCount }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { lstatApi } from '../../api'
import { PERIOD_TYPES } from '../../utils/dict'
import { formatSec, pct, downloadFile } from '../../utils/download'

// ---------------- 时间范围 ----------------
const periodType = ref('MONTH')
const dateRange = ref([])

const timeParams = computed(() => {
  const p = { periodType: periodType.value }
  if (periodType.value === 'CUSTOM' && dateRange.value && dateRange.value.length === 2) {
    p.startDate = dateRange.value[0]
    p.endDate = dateRange.value[1]
  }
  return p
})

const timeParamsValid = computed(() => periodType.value !== 'CUSTOM' || (dateRange.value && dateRange.value.length === 2))

function onPeriodChange() {
  if (!timeParamsValid.value) return
  reloadCurrent()
}

function reloadCurrent() {
  if (activeTab.value === 'org') reloadOrg()
  else loadRank()
}

// ---------------- 单位汇总 ----------------
const activeTab = ref('org')
const orgLoading = ref(false)
const orgData = ref({ children: [], hasSubLevel: false })
const levels = ref([]) // 下钻层级栈 [{type:'ORG'|'USER', orgId, orgName}]
const rootOrgName = ref('全部单位')

const currentLevel = computed(() => levels.value[levels.value.length - 1] || null)
const crumbs = computed(() => [rootOrgName.value, ...levels.value.map((l) => l.orgName)])

async function reloadOrg() {
  // 时间范围变化：重新从根加载
  levels.value = []
  orgLoading.value = true
  try {
    const d = await lstatApi.orgSummary(timeParams.value)
    orgData.value = d || { children: [], hasSubLevel: false }
    rootOrgName.value = d?.orgName || '全部单位'
  } finally {
    orgLoading.value = false
  }
}

async function drillSub(row) {
  orgLoading.value = true
  try {
    const d = await lstatApi.orgDetail(row.orgId, { ...timeParams.value, detailType: 'SUB_ORG' })
    levels.value.push({ type: 'ORG', orgId: row.orgId, orgName: row.orgName })
    orgData.value = d || { children: [], hasSubLevel: false }
  } finally {
    orgLoading.value = false
  }
}

const userLoading = ref(false)
const userList = ref([])
const userTotal = ref(0)
const userQuery = ref({ pageNum: 1, pageSize: 10 })

function drillUser(row) {
  levels.value.push({ type: 'USER', orgId: row.orgId, orgName: row.orgName })
  userQuery.value.pageNum = 1
  loadUsers()
}

async function loadUsers() {
  if (!currentLevel.value || currentLevel.value.type !== 'USER') return
  userLoading.value = true
  try {
    const d = await lstatApi.orgDetail(currentLevel.value.orgId, {
      ...timeParams.value,
      detailType: 'USER',
      pageNum: userQuery.value.pageNum,
      pageSize: userQuery.value.pageSize
    })
    userList.value = d.list || []
    userTotal.value = d.total || 0
  } finally {
    userLoading.value = false
  }
}

async function gotoCrumb(index) {
  if (index === 0) {
    reloadOrg()
    return
  }
  const target = levels.value[index - 1]
  levels.value = levels.value.slice(0, index - 1)
  if (target.type === 'ORG') {
    orgLoading.value = true
    try {
      orgData.value = await lstatApi.orgDetail(target.orgId, { ...timeParams.value, detailType: 'SUB_ORG' })
    } finally {
      orgLoading.value = false
    }
  } else {
    drillUser(target)
  }
}

// ---------------- 学习排行 ----------------
const rankLoading = ref(false)
const rankList = ref([])
const rankQuery = ref({ rankType: 'ORG', topN: 20 })
const chartRef = ref()
let chart = null

async function loadRank() {
  rankLoading.value = true
  try {
    rankList.value = await lstatApi.ranking({ ...rankQuery.value, ...timeParams.value }) || []
  } finally {
    rankLoading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const top10 = rankList.value.slice(0, 10)
  const names = top10.map((r) => (rankQuery.value.rankType === 'ORG' ? r.orgName : r.name)).reverse()
  const values = top10.map((r) => r.effectiveSec || 0).reverse()
  chart.setOption({
    title: { text: '有效学习时长 Top10', left: 'center', textStyle: { fontSize: 14 } },
    grid: { left: 140, right: 60, top: 40, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => `${params[0].name}<br/>有效时长：${formatSec(params[0].value)}`
    },
    xAxis: { type: 'value', axisLabel: { formatter: (v) => (v / 3600).toFixed(1) + 'h' } },
    yAxis: { type: 'category', data: names },
    series: [{ type: 'bar', data: values, barMaxWidth: 20, itemStyle: { color: '#409eff' } }]
  }, true)
  chart.resize()
}

watch(rankList, () => nextTick(renderChart))

function onTabChange(name) {
  if (!timeParamsValid.value) return
  if (name === 'rank') {
    loadRank()
  } else {
    reloadOrg()
  }
}

// ---------------- 完成资料数悬停提示 ----------------
const matTips = ref({}) // key → 资料名称数组（未加载为 undefined）
const matTipPending = new Set()

function rankTipKey(row) {
  return rankQuery.value.rankType === 'ORG' ? 'o' + row.orgId : 'u' + row.userId
}

function rankTipParams(row) {
  return rankQuery.value.rankType === 'ORG' ? { orgId: row.orgId } : { userId: row.userId }
}

async function ensureMatTip(key, params) {
  if (matTips.value[key] !== undefined || matTipPending.has(key)) return
  matTipPending.add(key)
  try {
    const list = await lstatApi.completedMaterials({ ...params, ...timeParams.value })
    matTips.value = { ...matTips.value, [key]: list && list.length ? list : ['（无记录）'] }
  } catch (e) {
    // 拦截器已统一报错；不缓存，下次悬停自动重试
  } finally {
    matTipPending.delete(key)
  }
}

// 时间范围变化后清空提示缓存
watch(timeParams, () => { matTips.value = {} })

// ---------------- 导出 ----------------
async function handleExport(exportType) {
  if (!timeParamsValid.value) {
    ElMessage.warning('请先选择完整的自定义日期范围')
    return
  }
  const names = { ORG_SUMMARY: '学习统计-单位汇总', USER_DETAIL: '学习统计-人员明细', BELOW_STANDARD: '学习统计-未达标名单' }
  await downloadFile('/admin/lstat/export', `${names[exportType] || '学习统计'}.xlsx`, { exportType, ...timeParams.value })
}

// ---------------- 生命周期 ----------------
function onResize() {
  chart && chart.resize()
}

onMounted(() => {
  reloadOrg()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.mat-count {
  cursor: pointer;
  color: #409eff;
  border-bottom: 1px dashed #409eff;
}
</style>

<!-- 提示框内容渲染在 body（teleport），不能用 scoped -->
<style>
.mat-tip-loading {
  color: #bbb;
}
.mat-tip-list {
  max-width: 360px;
  max-height: 300px;
  overflow-y: auto;
  line-height: 1.7;
}
</style>
