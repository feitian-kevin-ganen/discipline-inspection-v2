<template>
  <div class="page-container">
    <div class="page-card">
      <div class="toolbar">
        <el-select v-model="examId" placeholder="请选择测试场次" filterable style="width: 280px" @change="handleExamChange">
          <el-option v-for="e in examOptions" :key="e.examId" :label="e.examName" :value="e.examId" />
        </el-select>
        <div class="spacer"></div>
        <el-dropdown :disabled="!examId" trigger="click" @command="handleExport">
          <el-button type="primary" :disabled="!examId">
            导出<el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="SUMMARY">单位汇总表</el-dropdown-item>
              <el-dropdown-item command="DETAIL">人员成绩明细表</el-dropdown-item>
              <el-dropdown-item command="QUESTION">题目分析表</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <template v-if="examId">
        <div v-loading="overviewLoading" class="stat-cards">
          <div class="stat-card"><div class="num">{{ overview?.shouldCount ?? '-' }}</div><div class="label">应考人数</div></div>
          <div class="stat-card"><div class="num">{{ overview?.attendedCount ?? '-' }}</div><div class="label">实考人数</div></div>
          <div class="stat-card"><div class="num">{{ overview?.absentCount ?? '-' }}</div><div class="label">缺考人数</div></div>
          <div class="stat-card"><div class="num">{{ pct(overview?.attendRate) }}</div><div class="label">到考率</div></div>
          <div class="stat-card"><div class="num">{{ overview?.avgScore ?? '-' }}</div><div class="label">平均分</div></div>
          <div class="stat-card"><div class="num">{{ overview?.maxScore ?? '-' }}</div><div class="label">最高分</div></div>
          <div class="stat-card"><div class="num">{{ overview?.minScore ?? '-' }}</div><div class="label">最低分</div></div>
          <div class="stat-card"><div class="num">{{ pct(overview?.passRate) }}</div><div class="label">及格率</div></div>
          <div class="stat-card"><div class="num">{{ overview?.passCount ?? '-' }}</div><div class="label">及格人数</div></div>
        </div>

        <el-tabs v-model="activeTab">
          <!-- 单位统计 -->
          <el-tab-pane label="单位统计" name="org">
            <div v-if="orgMode === 'ORG'" v-loading="orgLoading">
              <el-breadcrumb v-if="orgStack.length" separator="/" style="margin-bottom: 12px">
                <el-breadcrumb-item><el-link type="primary" @click="backToOrg(0)">单位汇总</el-link></el-breadcrumb-item>
                <el-breadcrumb-item v-for="(o, i) in orgStack" :key="o.orgId">
                  <el-link v-if="i < orgStack.length - 1" type="primary" @click="backToOrg(i + 1)">{{ o.orgName }}</el-link>
                  <span v-else>{{ o.orgName }}</span>
                </el-breadcrumb-item>
              </el-breadcrumb>
              <el-table :data="orgChildren" border stripe>
                <el-table-column prop="orgName" label="单位名称" min-width="200" />
                <el-table-column prop="shouldCount" label="应考" width="90" align="center" />
                <el-table-column prop="attendedCount" label="实考" width="90" align="center" />
                <el-table-column label="到考率" width="100" align="center">
                  <template #default="{ row }">{{ pct(row.attendRate) }}</template>
                </el-table-column>
                <el-table-column prop="avgScore" label="平均分" width="100" align="center" />
                <el-table-column label="及格率" width="100" align="center">
                  <template #default="{ row }">{{ pct(row.passRate) }}</template>
                </el-table-column>
                <el-table-column label="排名" width="90" align="center">
                  <template #default="{ row }">
                    <span :class="{ 'top-rank': row.rank <= 3 }">{{ row.rank }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="160" align="center">
                  <template #default="{ row }">
                    <el-button v-if="hasSubLevel" type="primary" link @click="drillSubOrg(row)">下钻</el-button>
                    <el-button type="primary" link @click="drillUser(row)">人员明细</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div v-else v-loading="userLoading">
              <div class="toolbar">
                <el-button @click="orgMode = 'ORG'">返回单位汇总</el-button>
                <span class="user-org-name">{{ userOrg?.orgName }} · 人员明细</span>
              </div>
              <el-table :data="userList" border stripe>
                <el-table-column prop="name" label="姓名" width="120" />
                <el-table-column prop="jobNo" label="工号" width="120" />
                <el-table-column prop="score" label="成绩" width="90" align="center">
                  <template #default="{ row }">{{ row.score ?? '-' }}</template>
                </el-table-column>
                <el-table-column prop="grade" label="等级" width="80" align="center">
                  <template #default="{ row }">{{ row.grade || '-' }}</template>
                </el-table-column>
                <el-table-column label="是否及格" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.passed != null" :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '及格' : '不及格' }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rankInOrg" label="单位内排名" width="110" align="center">
                  <template #default="{ row }">{{ row.rankInOrg ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="答卷状态" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag :type="SHEET_STATUS[row.sheetStatus]?.type">{{ SHEET_STATUS[row.sheetStatus]?.label || row.sheetStatus }}</el-tag>
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
                  @size-change="loadUserDetail"
                  @current-change="loadUserDetail"
                />
              </div>
            </div>
          </el-tab-pane>

          <!-- 分数段分布 -->
          <el-tab-pane label="分数段分布" name="segment">
            <div v-loading="segLoading">
              <div ref="segChartRef" class="chart-box"></div>
              <el-table :data="segData" border stripe style="margin-top: 16px">
                <el-table-column prop="segment" label="分数段" width="140" align="center" />
                <el-table-column prop="count" label="人数" width="120" align="center" />
                <el-table-column label="占比" align="center">
                  <template #default="{ row }">{{ pct(row.ratio) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 等级分布 -->
          <el-tab-pane label="等级分布" name="grade">
            <div v-loading="gradeLoading">
              <div ref="gradeChartRef" class="chart-box"></div>
              <el-table :data="gradeData" border stripe style="margin-top: 16px">
                <el-table-column prop="gradeName" label="等级" width="140" align="center" />
                <el-table-column prop="count" label="人数" width="120" align="center" />
                <el-table-column label="占比" align="center">
                  <template #default="{ row }">{{ pct(row.ratio) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- 题目分析 -->
          <el-tab-pane label="题目分析" name="question">
            <div class="toolbar">
              <el-radio-group v-model="quesQuery.orderBy" @change="loadQuestionAnalysis">
                <el-radio-button value="WRONG_DESC">错误率最高</el-radio-button>
                <el-radio-button value="SEQ">按题号</el-radio-button>
              </el-radio-group>
            </div>
            <el-table v-loading="quesLoading" :data="quesData" border stripe row-key="sheetQuesId">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div v-if="row.optionDist?.length" class="option-dist">
                    <div class="option-dist-title">选项分布</div>
                    <el-table :data="row.optionDist" size="small" border>
                      <el-table-column prop="optKey" label="选项" width="100" align="center" />
                      <el-table-column prop="selectCount" label="选择人数" width="120" align="center" />
                      <el-table-column label="占比" align="center">
                        <template #default="{ row: opt }">{{ pct(opt.ratio) }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                  <span v-else class="no-dist">该题无选项分布数据（主观题/填空题）</span>
                </template>
              </el-table-column>
              <el-table-column prop="seq" label="题号" width="80" align="center" />
              <el-table-column label="题型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="QUES_TYPES[row.quesType]?.type">{{ QUES_TYPES[row.quesType]?.label || row.quesType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="stemText" label="题干" min-width="220" show-overflow-tooltip />
              <el-table-column prop="fullScore" label="满分" width="80" align="center" />
              <el-table-column prop="avgScore" label="平均得分" width="100" align="center" />
              <el-table-column label="正确率" width="100" align="center">
                <template #default="{ row }">{{ pct(row.correctRate) }}</template>
              </el-table-column>
              <el-table-column label="错误率" width="100" align="center">
                <template #default="{ row }">{{ pct(row.wrongRate) }}</template>
              </el-table-column>
              <el-table-column label="标签" min-width="140">
                <template #default="{ row }">
                  <el-tag v-for="t in row.tags || []" :key="t" size="small" effect="plain" style="margin-right: 4px">{{ t }}</el-tag>
                  <span v-if="!row.tags?.length">-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="请先选择测试场次" />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { examApi, statApi } from '../../api'
import { QUES_TYPES, SHEET_STATUS } from '../../utils/dict'
import { downloadFile, pct } from '../../utils/download'

const examOptions = ref([])
const examId = ref(null)
const activeTab = ref('org')

const overview = ref(null)
const overviewLoading = ref(false)

// 单位统计
const orgMode = ref('ORG')
const orgLoading = ref(false)
const orgChildren = ref([])
const hasSubLevel = ref(false)
const orgStack = ref([])
const userOrg = ref(null)
const userList = ref([])
const userTotal = ref(0)
const userLoading = ref(false)
const userQuery = reactive({ pageNum: 1, pageSize: 10 })

// 分数段
const segData = ref([])
const segLoading = ref(false)
const segChartRef = ref(null)
let segChart = null

// 等级分布
const gradeData = ref([])
const gradeLoading = ref(false)
const gradeChartRef = ref(null)
let gradeChart = null

// 题目分析
const quesData = ref([])
const quesLoading = ref(false)
const quesQuery = reactive({ orderBy: 'WRONG_DESC', topN: 20 })

async function loadExams() {
  const data = await examApi.page({ pageNum: 1, pageSize: 200 })
  examOptions.value = data.list || []
}

function handleExamChange() {
  if (!examId.value) return
  activeTab.value = 'org'
  orgMode.value = 'ORG'
  orgStack.value = []
  loadOverview()
  loadOrgSummary()
  loadSegments()
  loadGradeDist()
  loadQuestionAnalysis()
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await statApi.overview(examId.value)
  } finally {
    overviewLoading.value = false
  }
}

// ---------- 单位统计 ----------
async function loadOrgSummary() {
  orgLoading.value = true
  try {
    const data = await statApi.orgSummary(examId.value, {})
    orgChildren.value = data?.children || []
    hasSubLevel.value = !!data?.hasSubLevel
  } finally {
    orgLoading.value = false
  }
}

async function drillSubOrg(row) {
  orgLoading.value = true
  try {
    const data = await statApi.orgDetail(examId.value, row.orgId, { detailType: 'SUB_ORG' })
    orgStack.value.push({ orgId: row.orgId, orgName: row.orgName })
    orgChildren.value = data?.children || []
    hasSubLevel.value = !!data?.hasSubLevel
  } finally {
    orgLoading.value = false
  }
}

async function backToOrg(level) {
  if (level === 0) {
    orgStack.value = []
    await loadOrgSummary()
    return
  }
  const target = orgStack.value[level - 1]
  orgLoading.value = true
  try {
    const data = await statApi.orgDetail(examId.value, target.orgId, { detailType: 'SUB_ORG' })
    orgStack.value = orgStack.value.slice(0, level)
    orgChildren.value = data?.children || []
    hasSubLevel.value = !!data?.hasSubLevel
  } finally {
    orgLoading.value = false
  }
}

function drillUser(row) {
  userOrg.value = { orgId: row.orgId, orgName: row.orgName }
  userQuery.pageNum = 1
  orgMode.value = 'USER'
  loadUserDetail()
}

async function loadUserDetail() {
  userLoading.value = true
  try {
    const data = await statApi.orgDetail(examId.value, userOrg.value.orgId, {
      detailType: 'USER',
      pageNum: userQuery.pageNum,
      pageSize: userQuery.pageSize
    })
    userList.value = data?.list || []
    userTotal.value = data?.total || 0
  } finally {
    userLoading.value = false
  }
}

// ---------- 分数段分布 ----------
async function loadSegments() {
  segLoading.value = true
  try {
    segData.value = (await statApi.scoreSegments(examId.value)) || []
  } finally {
    segLoading.value = false
  }
}

function renderSegChart() {
  if (!segChartRef.value) return
  if (!segChart) segChart = echarts.init(segChartRef.value)
  segChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 30, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: segData.value.map((d) => d.segment), name: '分数段' },
    yAxis: { type: 'value', name: '人数', minInterval: 1 },
    series: [{ type: 'bar', data: segData.value.map((d) => d.count), barMaxWidth: 48, itemStyle: { color: '#409eff' } }]
  })
}

// ---------- 等级分布 ----------
async function loadGradeDist() {
  gradeLoading.value = true
  try {
    gradeData.value = (await statApi.gradeDist(examId.value)) || []
  } finally {
    gradeLoading.value = false
  }
}

function renderGradeChart() {
  if (!gradeChartRef.value) return
  if (!gradeChart) gradeChart = echarts.init(gradeChartRef.value)
  gradeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      label: { formatter: '{b} {d}%' },
      data: gradeData.value.map((d) => ({ name: d.gradeName, value: d.count }))
    }]
  })
}

// ---------- 题目分析 ----------
async function loadQuestionAnalysis() {
  if (!examId.value) return
  quesLoading.value = true
  try {
    quesData.value = (await statApi.questionAnalysis(examId.value, { ...quesQuery })) || []
  } finally {
    quesLoading.value = false
  }
}

// 图表：tab 激活且数据就绪后再 init（隐藏容器尺寸为 0 的问题）
watch([activeTab, segData, segLoading], async () => {
  if (activeTab.value === 'segment' && !segLoading.value) {
    await nextTick()
    renderSegChart()
  }
})
watch([activeTab, gradeData, gradeLoading], async () => {
  if (activeTab.value === 'grade' && !gradeLoading.value) {
    await nextTick()
    renderGradeChart()
  }
})

function handleResize() {
  segChart?.resize()
  gradeChart?.resize()
}

// ---------- 导出 ----------
async function handleExport(exportType) {
  const names = { SUMMARY: '单位汇总表', DETAIL: '人员成绩明细表', QUESTION: '题目分析表' }
  await downloadFile(`/admin/stat/exams/${examId.value}/export`, `测试统计-${names[exportType] || exportType}.xlsx`, { exportType })
}

onMounted(() => {
  loadExams()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  segChart?.dispose()
  gradeChart?.dispose()
})
</script>

<style scoped>
.chart-box {
  height: 360px;
}
.top-rank {
  color: #f56c6c;
  font-weight: 700;
}
.user-org-name {
  font-weight: 600;
  color: #303133;
}
.option-dist {
  padding: 8px 40px;
}
.option-dist-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #606266;
}
.no-dist {
  padding: 8px 40px;
  color: #909399;
}
</style>
