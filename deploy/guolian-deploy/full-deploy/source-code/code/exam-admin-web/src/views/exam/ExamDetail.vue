<template>
  <div class="page-container">
    <!-- 顶部信息卡片 -->
    <div class="page-card" v-loading="detailLoading">
      <div class="toolbar">
        <el-button @click="router.push('/exams')">返回列表</el-button>
        <h3 style="margin: 0">{{ detail.examName || '场次详情' }}</h3>
        <el-tag v-if="EXAM_STATUS[detail.status]" :type="EXAM_STATUS[detail.status].type">{{ EXAM_STATUS[detail.status].label }}</el-tag>
        <div class="spacer" />
        <el-button v-if="detail.status === 'NOT_STARTED'" type="success" @click="onStatusAction('START')">开始</el-button>
        <el-button v-if="detail.status === 'IN_PROGRESS'" type="warning" @click="onStatusAction('END')">结束</el-button>
        <el-button v-if="detail.status === 'NOT_STARTED'" type="warning" @click="onStatusAction('CANCEL')">取消</el-button>
        <el-button v-if="detail.status === 'IN_PROGRESS'" type="primary" @click="onExtend">延长测试</el-button>
        <el-button v-if="detail.status === 'IN_PROGRESS'" type="danger" @click="openForceSubmit">强制收卷</el-button>
        <el-button type="primary" plain @click="notifyDialog = true">推送通知</el-button>
        <el-button v-if="detail.status === 'ENDED'" type="primary" plain @click="openRetake">补考设置</el-button>
        <el-button v-if="detail.status === 'ENDED'" type="success" @click="onPublish">发布成绩</el-button>
      </div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="测试名称">{{ detail.examName }}</el-descriptions-item>
        <el-descriptions-item label="关联试卷">{{ detail.paperName }}</el-descriptions-item>
        <el-descriptions-item label="测试时长">{{ detail.durationMin != null ? detail.durationMin + ' 分钟' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="开考时间">{{ fmtTime(detail.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ fmtTime(detail.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="名单数 / 实考">{{ detail.candidateCount ?? 0 }} / {{ detail.attendedCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="迟到时限">{{ detail.lateEnterMin ? detail.lateEnterMin + ' 分钟' : '不允许迟到' }}</el-descriptions-item>
        <el-descriptions-item label="切屏上限">{{ detail.switchScreenLimit != null ? detail.switchScreenLimit + ' 次' : '系统默认' }}</el-descriptions-item>
        <el-descriptions-item label="提前交卷">
          {{ detail.allowEarlySubmit === false ? '不允许' : `开考 ${detail.earlySubmitAfterMin ?? 0} 分钟后允许` }}
        </el-descriptions-item>
        <el-descriptions-item label="题目乱序">{{ detail.shuffleQuestion === false ? '关闭' : '开启' }}</el-descriptions-item>
        <el-descriptions-item label="选项乱序">{{ detail.shuffleOption === false ? '关闭' : '开启' }}</el-descriptions-item>
        <el-descriptions-item label="测试须知">{{ detail.notice || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 标签页 -->
    <div class="page-card" style="margin-top: 16px">
      <el-tabs v-model="activeTab">
        <!-- 考生名单 -->
        <el-tab-pane label="考生名单" name="candidates">
          <div class="toolbar">
            <el-input v-model="candQuery.keyword" placeholder="姓名 / 工号" clearable style="width: 200px" @keyup.enter="onCandSearch" @clear="onCandSearch" />
            <el-select v-model="candQuery.sheetStatus" placeholder="答卷状态" clearable style="width: 150px" @change="onCandSearch">
              <el-option v-for="(v, k) in SHEET_STATUS" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button type="primary" @click="onCandSearch">查询</el-button>
            <el-button @click="onCandReset">重置</el-button>
            <div class="spacer" />
            <el-button type="primary" @click="openScopeDialog">按组织生成名单</el-button>
            <el-button type="primary" plain @click="openAddDialog">追加考生</el-button>
            <el-button type="danger" plain :disabled="!candSelection.length" @click="onRemoveCandidates">剔除所选</el-button>
          </div>
          <el-table :data="candList" v-loading="candLoading" border @selection-change="(rows) => (candSelection = rows)">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="姓名" width="110" />
            <el-table-column prop="jobNo" label="工号" width="120" />
            <el-table-column prop="orgNameSnapshot" label="所属单位" min-width="160" show-overflow-tooltip />
            <el-table-column label="答卷状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="SHEET_STATUS[row.sheetStatus]" :type="SHEET_STATUS[row.sheetStatus].type">{{ SHEET_STATUS[row.sheetStatus].label }}</el-tag>
                <span v-else>{{ row.sheetStatus }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="enterTime" label="进入时间" width="165" />
            <el-table-column prop="submitTime" label="交卷时间" width="165" />
            <el-table-column prop="objectiveScore" label="客观题分" width="90" align="center" />
            <el-table-column prop="totalScore" label="总分" width="80" align="center" />
            <el-table-column prop="grade" label="等级" width="80" align="center" />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="candQuery.pageNum"
              v-model:page-size="candQuery.pageSize"
              :total="candTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadCandidates"
              @current-change="loadCandidates"
            />
          </div>
        </el-tab-pane>

        <!-- 测试监控 -->
        <el-tab-pane label="测试监控" name="monitor">
          <div class="toolbar">
            <el-link type="danger" underline="never" @click="$router.push(`/sys/logs/exam-abnormal?examId=${examId}`)">
              切屏异常提示：查看本场测试异常日志
            </el-link>
            <div class="spacer" />
            <el-button type="primary" plain :loading="monitorLoading" @click="loadMonitor">刷新</el-button>
          </div>
          <div class="stat-cards" v-loading="monitorLoading">
            <div class="stat-card"><div class="num">{{ overview.shouldCount ?? '-' }}</div><div class="label">应考人数</div></div>
            <div class="stat-card"><div class="num">{{ overview.attendedCount ?? '-' }}</div><div class="label">实考人数</div></div>
            <div class="stat-card"><div class="num">{{ overview.absentCount ?? '-' }}</div><div class="label">缺考人数</div></div>
            <div class="stat-card"><div class="num">{{ pct(overview.attendRate) }}</div><div class="label">到考率</div></div>
            <div class="stat-card"><div class="num">{{ overview.avgScore ?? '-' }}</div><div class="label">平均分</div></div>
            <div class="stat-card"><div class="num">{{ overview.maxScore ?? '-' }}</div><div class="label">最高分</div></div>
            <div class="stat-card"><div class="num">{{ overview.minScore ?? '-' }}</div><div class="label">最低分</div></div>
            <div class="stat-card"><div class="num">{{ pct(overview.passRate) }}</div><div class="label">及格率</div></div>
          </div>
          <p class="muted">数据每 30 秒自动刷新一次。</p>
        </el-tab-pane>

        <!-- 成绩发布信息 -->
        <el-tab-pane label="成绩发布信息" name="publish">
          <template v-if="detail.status === 'SCORED_PUBLISHED'">
            <el-alert type="success" :closable="false" title="本场测试成绩已发布" style="margin-bottom: 16px" />
            <el-descriptions :column="3" border>
              <el-descriptions-item label="发布人数">{{ publishInfo.publishedCount ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="平均分">{{ publishInfo.avgScore ?? detail.avgScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="及格率">{{ publishInfo.passRate != null ? pct(publishInfo.passRate) : '-' }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <el-empty v-else :description="`当前状态为「${EXAM_STATUS[detail.status]?.label || detail.status || '-'}」，成绩发布后在此展示发布结果`" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 按组织生成名单 -->
    <el-dialog v-model="scopeDialog" title="按组织生成名单" width="520px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
        title="重复生成将全量重建名单（已在考 / 已交卷的场次禁止重建），名单以最后一次生成为准。" />
      <el-tree
        ref="orgTreeRef"
        :data="orgTree"
        :props="{ label: 'orgName', children: 'children' }"
        node-key="orgId"
        show-checkbox
        default-expand-all
        style="max-height: 320px; overflow: auto"
      />
      <div v-if="scopeRows.length" style="margin-top: 12px">
        <div v-for="row in scopeRows" :key="row.orgId" class="scope-row">
          <span class="scope-name">{{ row.orgName }}</span>
          <el-switch v-model="row.includeSub" inline-prompt active-text="含下级" inactive-text="不含下级" />
        </div>
      </div>
      <template #footer>
        <el-button @click="scopeDialog = false">取消</el-button>
        <el-button type="primary" :loading="scopeSaving" @click="onGenCandidates">生成名单</el-button>
      </template>
    </el-dialog>

    <!-- 追加考生 -->
    <el-dialog v-model="addDialog" title="追加考生" width="760px" @open="loadAddUsers">
      <div class="toolbar">
        <el-input v-model="addQuery.keyword" placeholder="姓名 / 工号" clearable style="width: 180px" @keyup.enter="onAddSearch" @clear="onAddSearch" />
        <el-tree-select
          v-model="addQuery.orgId"
          :data="orgTree"
          :props="{ label: 'orgName', children: 'children' }"
          node-key="orgId"
          check-strictly
          clearable
          placeholder="按单位筛选"
          style="width: 220px"
          @change="onAddSearch"
        />
        <el-button type="primary" @click="onAddSearch">查询</el-button>
        <el-button @click="onAddReset">重置</el-button>
      </div>
      <el-table ref="addTableRef" :data="addList" v-loading="addLoading" border height="340" @selection-change="(rows) => (addSelection = rows)">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="jobNo" label="工号" width="120" />
        <el-table-column prop="orgName" label="所属单位" min-width="160" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="addQuery.pageNum"
          v-model:page-size="addQuery.pageSize"
          :total="addTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadAddUsers"
          @current-change="loadAddUsers"
        />
      </div>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="addSaving" :disabled="!addSelection.length" @click="onAddCandidates">
          追加所选（{{ addSelection.length }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- 强制收卷 -->
    <el-dialog v-model="forceDialog" title="强制收卷" width="560px" @open="loadInExamCandidates">
      <el-form label-width="90px">
        <el-form-item label="收卷对象">
          <el-select v-model="forceUserIds" multiple filterable placeholder="不选择则对在考考生全部收卷" style="width: 100%">
            <el-option v-for="c in inExamCandidates" :key="c.userId" :label="`${c.name}（${c.jobNo}）`" :value="c.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="收卷原因">
          <el-input v-model="forceReason" placeholder="例如：异常处理" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forceDialog = false">取消</el-button>
        <el-button type="danger" :loading="forceSaving" @click="onForceSubmit">确认收卷</el-button>
      </template>
    </el-dialog>

    <!-- 推送通知 -->
    <el-dialog v-model="notifyDialog" title="推送测试通知" width="560px" @open="loadNotifyCandidates">
      <el-alert
        v-if="!notifyCandidates.length"
        type="warning"
        :closable="false"
        style="margin-bottom: 12px"
        title="考生名单为空，请先在「考生名单」页签生成或追加考生"
      >
        <div style="margin-top: 8px">
          <el-button size="small" type="primary" @click="notifyDialog = false; openScopeDialog()">按组织生成名单</el-button>
          <el-button size="small" type="primary" plain @click="notifyDialog = false; openAddDialog()">追加考生</el-button>
        </div>
      </el-alert>
      <el-form label-width="90px">
        <el-form-item v-if="notifyCandidates.length" label="接收考生">
          <el-select v-model="notifyUserIds" multiple filterable collapse-tags :max-collapse-tags="5" placeholder="选择接收考生" style="width: 100%">
            <el-option v-for="c in notifyCandidates" :key="c.userId" :label="`${c.name}（${c.jobNo}）`" :value="c.userId" />
          </el-select>
          <div class="muted" style="line-height: 1.4; margin-top: 4px">默认全选名单内 {{ notifyCandidates.length }} 名考生，可删减后定向推送</div>
        </el-form-item>
        <el-form-item label="通知内容">
          <el-input v-model="notifyMessage" type="textarea" :rows="4" placeholder="例如：请于 8 月 25 日 9:00-11:00 参加统考" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="推送渠道">
          <el-checkbox :model-value="true" disabled>站内消息（INBOX）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notifyDialog = false">取消</el-button>
        <el-button type="primary" :loading="notifySaving" :disabled="!notifyUserIds.length" @click="onNotify">
          推送{{ notifyUserIds.length ? `（${notifyUserIds.length} 人）` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 补考设置 -->
    <el-dialog v-model="retakeDialog" title="补考设置" width="560px" @open="loadRetakeCandidates">
      <el-form label-width="110px">
        <el-form-item label="补考时间窗口" required>
          <el-date-picker
            v-model="retakeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="补考开始时间"
            end-placeholder="补考结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="补考考生" required>
          <el-select v-model="retakeUserIds" multiple filterable placeholder="选择缺考 / 不及格考生" style="width: 100%">
            <el-option v-for="c in retakeCandidates" :key="c.userId" :label="`${c.name}（${c.jobNo}）`" :value="c.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retakeDialog = false">取消</el-button>
        <el-button type="primary" :loading="retakeSaving" @click="onRetake">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examApi, markApi, orgApi, statApi, userApi } from '../../api'
import { EXAM_STATUS, SHEET_STATUS } from '../../utils/dict'
import { pct } from '../../utils/download'
import { fmtTime } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const examId = computed(() => route.params.id)

// ---------- 详情 ----------
const detailLoading = ref(false)
const detail = ref({})
const activeTab = ref('candidates')
const publishInfo = ref({})

async function loadDetail() {
  detailLoading.value = true
  try {
    detail.value = await examApi.detail(examId.value)
  } finally {
    detailLoading.value = false
  }
}

const STATUS_ACTION_TIPS = {
  START: { title: '开始测试', msg: '确认提前开考？考生将可进入本场测试。' },
  END: { title: '结束测试', msg: '确认提前结束本场测试？将强制收取未交卷答卷。' },
  CANCEL: { title: '取消测试', msg: '确认取消本场测试？取消后不可恢复。' }
}

async function onStatusAction(action) {
  const tip = STATUS_ACTION_TIPS[action]
  await ElMessageBox.confirm(tip.msg, tip.title, { type: 'warning' })
  await examApi.setStatus(examId.value, action)
  ElMessage.success('操作成功')
  await loadDetail()
  loadCandidates()
}

async function onExtend() {
  const { value } = await ElMessageBox.prompt('请输入延长时间（分钟），进行中场次的窗口结束时间将顺延：', '延长测试', {
    inputPattern: /^[1-9]\d*$/,
    inputErrorMessage: '请输入正整数分钟数'
  })
  await examApi.extend(examId.value, Number(value))
  ElMessage.success('延长成功')
  loadDetail()
}

async function onPublish() {
  await ElMessageBox.confirm('确认发布本场测试成绩？发布后考生可查询成绩。', '发布成绩', { type: 'warning' })
  const data = await markApi.publish(examId.value)
  publishInfo.value = data || {}
  ElMessage.success(`发布成功：发布 ${data.publishedCount ?? '-'} 人，平均分 ${data.avgScore ?? '-'}，及格率 ${pct(data.passRate)}`)
  await loadDetail()
  activeTab.value = 'publish'
}

// ---------- 考生名单 ----------
const candLoading = ref(false)
const candList = ref([])
const candTotal = ref(0)
const candSelection = ref([])
const candQuery = reactive({ keyword: '', sheetStatus: '', pageNum: 1, pageSize: 10 })

async function loadCandidates() {
  candLoading.value = true
  try {
    const data = await examApi.candidatePage(examId.value, candQuery)
    candList.value = data.list || []
    candTotal.value = data.total || 0
  } finally {
    candLoading.value = false
  }
}

function onCandSearch() {
  candQuery.pageNum = 1
  loadCandidates()
}

function onCandReset() {
  candQuery.keyword = ''
  candQuery.sheetStatus = ''
  onCandSearch()
}

async function onRemoveCandidates() {
  const userIds = candSelection.value.map((r) => r.userId)
  await ElMessageBox.confirm(`确认剔除所选 ${userIds.length} 名考生？已进入测试的考生不可剔除。`, '剔除考生', { type: 'warning' })
  const data = await examApi.removeCandidates(examId.value, userIds)
  const skippedTip = data.skipped?.length ? `，${data.skipped.length} 人被跳过` : ''
  ElMessage.success(`已剔除 ${data.removedCount ?? 0} 人${skippedTip}`)
  loadCandidates()
  loadDetail()
}

// ---------- 按组织生成名单 ----------
const scopeDialog = ref(false)
const scopeSaving = ref(false)
const orgTree = ref([])
const orgTreeRef = ref()
const scopeRows = ref([])

async function ensureOrgTree() {
  if (!orgTree.value.length) {
    orgTree.value = await orgApi.tree()
  }
}

function openScopeDialog() {
  scopeDialog.value = true
  ensureOrgTree()
}

function findOrgName(nodes, orgId) {
  for (const n of nodes) {
    if (n.orgId === orgId) return n.orgName
    const hit = n.children && findOrgName(n.children, orgId)
    if (hit) return hit
  }
  return null
}

async function onGenCandidates() {
  const checked = orgTreeRef.value.getCheckedKeys()
  if (!checked.length) {
    ElMessage.warning('请先勾选组织节点')
    return
  }
  // 已有配置行的节点沿用其 includeSub 设置，新勾选节点默认含下级
  scopeRows.value = checked.map((orgId) => {
    const existed = scopeRows.value.find((r) => r.orgId === orgId)
    return existed || { orgId, orgName: findOrgName(orgTree.value, orgId) || orgId, includeSub: true }
  })
  scopeSaving.value = true
  try {
    const scopes = scopeRows.value.map((r) => ({ orgId: r.orgId, includeSub: r.includeSub }))
    const data = await examApi.genCandidates(examId.value, scopes)
    ElMessage.success(`名单生成成功，共 ${data.candidateCount} 人`)
    scopeDialog.value = false
    loadCandidates()
    loadDetail()
  } finally {
    scopeSaving.value = false
  }
}

// ---------- 追加考生 ----------
const addDialog = ref(false)
const addLoading = ref(false)
const addSaving = ref(false)
const addList = ref([])
const addTotal = ref(0)
const addSelection = ref([])
const addQuery = reactive({ keyword: '', orgId: null, pageNum: 1, pageSize: 10 })

function openAddDialog() {
  addDialog.value = true
  ensureOrgTree()
}

async function loadAddUsers() {
  addLoading.value = true
  try {
    const params = { ...addQuery, status: 1 }
    if (!params.orgId) delete params.orgId
    const data = await userApi.page(params)
    addList.value = data.list || []
    addTotal.value = data.total || 0
  } finally {
    addLoading.value = false
  }
}

function onAddSearch() {
  addQuery.pageNum = 1
  loadAddUsers()
}

function onAddReset() {
  addQuery.keyword = ''
  addQuery.orgId = null
  onAddSearch()
}

async function onAddCandidates() {
  const userIds = addSelection.value.map((r) => r.userId)
  addSaving.value = true
  try {
    const data = await examApi.addCandidates(examId.value, userIds)
    const skippedTip = data.skipped?.length ? `，${data.skipped.length} 人被跳过（${data.skipped[0].reason}等）` : ''
    ElMessage.success(`已追加 ${data.addedCount ?? 0} 人${skippedTip}`)
    addDialog.value = false
    addSelection.value = []
    loadCandidates()
    loadDetail()
  } finally {
    addSaving.value = false
  }
}

// ---------- 强制收卷 ----------
const forceDialog = ref(false)
const forceSaving = ref(false)
const forceUserIds = ref([])
const forceReason = ref('')
const inExamCandidates = ref([])

function openForceSubmit() {
  forceUserIds.value = []
  forceReason.value = ''
  forceDialog.value = true
}

async function loadInExamCandidates() {
  const data = await examApi.candidatePage(examId.value, { sheetStatus: 'IN_EXAM', pageNum: 1, pageSize: 500 })
  inExamCandidates.value = data.list || []
}

async function onForceSubmit() {
  const tip = forceUserIds.value.length
    ? `确认对所选 ${forceUserIds.value.length} 名考生强制收卷？`
    : '确认对全部在考考生强制收卷？'
  await ElMessageBox.confirm(tip, '强制收卷', { type: 'warning' })
  forceSaving.value = true
  try {
    const data = await examApi.forceSubmit(examId.value, { userIds: forceUserIds.value, reason: forceReason.value })
    ElMessage.success(`已强制收卷 ${data.forcedCount ?? 0} 份`)
    forceDialog.value = false
    loadCandidates()
    loadDetail()
  } finally {
    forceSaving.value = false
  }
}

// ---------- 推送通知 ----------
const notifyDialog = ref(false)
const notifySaving = ref(false)
const notifyMessage = ref('')
const notifyCandidates = ref([])
const notifyUserIds = ref([])

async function loadNotifyCandidates() {
  const data = await examApi.candidatePage(examId.value, { pageNum: 1, pageSize: 500 })
  notifyCandidates.value = data.list || []
  // 默认全选名单内考生
  notifyUserIds.value = notifyCandidates.value.map((c) => c.userId)
}

async function onNotify() {
  if (!notifyUserIds.value.length) {
    ElMessage.warning('请选择接收考生')
    return
  }
  if (!notifyMessage.value.trim()) {
    ElMessage.warning('请输入通知内容')
    return
  }
  notifySaving.value = true
  try {
    const data = await examApi.notify(examId.value, {
      message: notifyMessage.value,
      channels: ['INBOX'],
      userIds: notifyUserIds.value
    })
    ElMessage.success(`推送成功，已发送 ${data.sentCount ?? 0} 人`)
    notifyDialog.value = false
    notifyMessage.value = ''
    loadDetail()
  } finally {
    notifySaving.value = false
  }
}

// ---------- 补考设置 ----------
const retakeDialog = ref(false)
const retakeSaving = ref(false)
const retakeRange = ref([])
const retakeUserIds = ref([])
const retakeCandidates = ref([])

function openRetake() {
  retakeRange.value = []
  retakeUserIds.value = []
  retakeDialog.value = true
}

async function loadRetakeCandidates() {
  const data = await examApi.candidatePage(examId.value, { pageNum: 1, pageSize: 500 })
  retakeCandidates.value = data.list || []
}

async function onRetake() {
  if (!retakeRange.value || retakeRange.value.length !== 2) {
    ElMessage.warning('请选择补考时间窗口')
    return
  }
  if (!retakeUserIds.value.length) {
    ElMessage.warning('请选择补考考生')
    return
  }
  retakeSaving.value = true
  try {
    await examApi.retake(examId.value, {
      retakeStartTime: retakeRange.value[0],
      retakeEndTime: retakeRange.value[1],
      userIds: retakeUserIds.value
    })
    ElMessage.success('补考设置成功')
    retakeDialog.value = false
    loadDetail()
  } finally {
    retakeSaving.value = false
  }
}

// ---------- 测试监控 ----------
const monitorLoading = ref(false)
const overview = ref({})
let monitorTimer = null

async function loadMonitor() {
  monitorLoading.value = true
  try {
    overview.value = await statApi.overview(examId.value)
  } finally {
    monitorLoading.value = false
  }
}

onMounted(() => {
  loadDetail()
  loadCandidates()
  loadMonitor()
  monitorTimer = setInterval(loadMonitor, 30000)
})

onUnmounted(() => {
  if (monitorTimer) clearInterval(monitorTimer)
})
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
.scope-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid #ebeef5;
}
.scope-name {
  font-size: 13px;
  color: #303133;
}
</style>
