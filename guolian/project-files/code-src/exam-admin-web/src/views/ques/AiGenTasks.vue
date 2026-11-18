<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="card-title">生成中的题目列表</span>
          <div>
            <el-button size="small" @click="$router.push('/ques/ai-gen')">返回AI生成</el-button>
            <el-button size="small" @click="loadTasks">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 任务列表：一个资料一个任务行 -->
      <el-table v-loading="tasksLoading" :data="tasks" highlight-current-row row-key="taskId" @current-change="onTaskSelect">
        <el-table-column prop="materialTitle" label="资料名称" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="{ RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger' }[row.status] || 'info'">
              {{ { RUNNING: '生成中', SUCCESS: '生成成功', FAILED: '生成失败' }[row.status] || row.status }}
              <el-icon v-if="row.status === 'RUNNING'" class="is-loading gen-spinner"><Loading /></el-icon>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170" align="center">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170" align="center">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="生成时长" width="100" align="center">
          <template #default="{ row }">{{ fmtDuration(row.durationSec) }}</template>
        </el-table-column>
        <el-table-column prop="quesCount" label="题目数" width="80" align="center" />
        <el-table-column prop="typeSummary" label="题目明细" min-width="200" show-overflow-tooltip />
        <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onViewTask(row)">查看题目</el-button>
            <el-button link type="primary" size="small" @click="onRefreshTask(row)">刷新</el-button>
            <el-button v-if="row.status !== 'RUNNING'" link type="danger" size="small" @click="onDeleteTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!tasksLoading && tasks.length === 0" description="暂无生成任务" :image-size="70" />

      <!-- 选中任务的题目清单 -->
      <template v-if="currentTask">
        <el-divider content-position="left">
          「{{ currentTask.materialTitle }}」生成题目（{{ questions.length }}）
        </el-divider>
        <el-table v-loading="questionsLoading" :data="questions" row-key="id">
          <el-table-column label="题干" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">{{ row.stem }}</template>
          </el-table-column>
          <el-table-column label="题型" width="80" align="center">
            <template #default="{ row }">{{ TYPE_NAME[row.quesType] || row.quesType }}</template>
          </el-table-column>
          <el-table-column label="难度" width="70" align="center">
            <template #default="{ row }">{{ DIFF_NAME[row.difficulty] || row.difficulty }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="{ PENDING: 'info', ADOPTED: 'success', DISCARDED: 'danger' }[row.status]" size="small">
                {{ { PENDING: '待处理', ADOPTED: '已采纳', DISCARDED: '已丢弃' }[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="success" size="small" @click="onAdopt(row)">采纳</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="onDiscard(row)">丢弃</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!questionsLoading && questions.length === 0" :description="currentTask.status === 'RUNNING' ? '正在生成中，请稍后，题目会自动刷新…' : '该任务暂无题目'" :image-size="60" />
      </template>
    </el-card>

    <!-- 查看/编辑对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'edit' ? '编辑生成题目' : '查看生成题目'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="题型">
          <el-tag>{{ TYPE_NAME[dialog.form.quesType] || dialog.form.quesType }}</el-tag>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-if="dialog.mode === 'edit'" v-model="dialog.form.difficulty" style="width: 160px">
            <el-option v-for="d in DIFFICULTIES" :key="d.key" :label="d.name" :value="d.key" />
          </el-select>
          <el-tag v-else>{{ DIFF_NAME[dialog.form.difficulty] }}</el-tag>
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="dialog.form.stem" type="textarea" :rows="3" :readonly="dialog.mode === 'view'" />
        </el-form-item>
        <template v-if="dialog.form.options && dialog.form.options.length">
          <el-form-item v-for="(opt, i) in dialog.form.options" :key="i" :label="'选项 ' + opt.optKey">
            <div class="opt-row">
              <el-checkbox v-if="dialog.mode === 'edit'" v-model="opt.isAnswer">正确</el-checkbox>
              <el-tag v-else :type="opt.isAnswer ? 'success' : 'info'" size="small">{{ opt.isAnswer ? '正确' : '错误' }}</el-tag>
              <el-input v-model="opt.content" :readonly="dialog.mode === 'view'" />
            </div>
          </el-form-item>
        </template>
        <el-form-item label="解析">
          <el-input v-model="dialog.form.analysis" type="textarea" :rows="2" :readonly="dialog.mode === 'view'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button v-if="dialog.mode === 'edit'" type="primary" :loading="dialog.saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { aiGenApi } from '../../api'

const TYPE_NAME = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', FILL: '填空', ESSAY: '简答' }
const DIFFICULTIES = [
  { key: 'EASY', name: '易' },
  { key: 'MEDIUM', name: '中' },
  { key: 'HARD', name: '难' }
]
const DIFF_NAME = { EASY: '易', MEDIUM: '中', HARD: '难' }

const tasks = ref([])
const tasksLoading = ref(false)
const currentTask = ref(null)
const questions = ref([])
const questionsLoading = ref(false)
let taskTimer = null // 任务列表轮询（5s）
let quesTimer = null // 题目明细轮询（5s，仅当前任务生成中时开启）
let quesFinalSyncedId = null // 已对哪个任务做过"终态后的最后一次题目刷新"

// YYYY-MM-DD HH:mm:ss（24小时制，精确到秒）
function fmtTime(t) {
  if (!t) return '—'
  const d = new Date(String(t).replace(' ', 'T'))
  if (isNaN(d.getTime())) return String(t).slice(0, 19)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// 生成时长：mm分ss秒 / hh小时mm分
function fmtDuration(sec) {
  if (sec == null) return '—'
  if (sec < 60) return `${sec}秒`
  const m = Math.floor(sec / 60)
  const s = sec % 60
  if (m < 60) return s ? `${m}分${s}秒` : `${m}分钟`
  const h = Math.floor(m / 60)
  return `${h}小时${m % 60}分`
}

// 题目明细自动刷新：仅当当前任务处于生成中时每 5s 刷新一次；
// 任务到达终态（生成成功/失败）后做最后一次刷新并停止，不再轮询
function syncQuesPolling() {
  const running = currentTask.value && currentTask.value.status === 'RUNNING'
  if (running) {
    if (!quesTimer) {
      quesTimer = setInterval(() => {
        if (currentTask.value) loadQuestions(currentTask.value.taskId, true)
      }, 5000)
    }
  } else if (quesTimer) {
    clearInterval(quesTimer)
    quesTimer = null
  }
}

// 任务列表自动刷新：仅当存在生成中任务时每 5s 轮询一次；
// 全部任务到达终态后自动停止（手动「刷新」或出现新生成中任务时恢复）
// 轮询与手动刷新均为静默更新（不触发整表 loading 遮罩，仅局部更新状态等字段）
function syncTaskPolling() {
  const hasRunning = tasks.value.some((t) => t.status === 'RUNNING')
  if (hasRunning) {
    if (!taskTimer) taskTimer = setInterval(() => loadTasks(true, true), 5000)
  } else if (taskTimer) {
    clearInterval(taskTimer)
    taskTimer = null
  }
}

async function loadTasks(keepSelection = true, silent = false) {
  if (!silent) tasksLoading.value = true
  try {
    tasks.value = await aiGenApi.tasks()
    if (keepSelection && currentTask.value) {
      const still = tasks.value.find((t) => t.taskId === currentTask.value.taskId)
      currentTask.value = still || null
    }
    if (!currentTask.value && tasks.value.length) {
      // 默认展示最新一条任务的题目明细
      currentTask.value = tasks.value[0]
      questions.value = []
    }
    if (currentTask.value) {
      const running = currentTask.value.status === 'RUNNING'
      if (running) {
        quesFinalSyncedId = null
        await loadQuestions(currentTask.value.taskId, silent)
      } else if (quesFinalSyncedId !== currentTask.value.taskId) {
        // 终态任务只刷一次题目，之后停止自动刷新
        quesFinalSyncedId = currentTask.value.taskId
        await loadQuestions(currentTask.value.taskId, silent)
      }
    } else {
      questions.value = []
    }
    syncQuesPolling()
    syncTaskPolling()
  } catch (e) {
    // 拦截器已提示
  } finally {
    if (!silent) tasksLoading.value = false
  }
}

function onTaskSelect(task) {
  currentTask.value = task || null
  questions.value = []
  quesFinalSyncedId = null
  if (task) loadQuestions(task.taskId)
  syncQuesPolling()
}

// 查看题目：选中该任务并加载其题目明细
function onViewTask(row) {
  if (currentTask.value && currentTask.value.taskId === row.taskId) {
    loadQuestions(row.taskId)
  } else {
    onTaskSelect(row)
  }
}

// 刷新：静默刷新任务列表与当前任务状态（不整表 loading，仅局部更新字段）
function onRefreshTask() {
  loadTasks(true, true)
}

// 删除任务（生成中的任务后端会拒绝）
async function onDeleteTask(row) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.materialTitle}」及其生成题目吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      confirmButtonClass: 'el-button--danger'
    })
  } catch (e) {
    return
  }
  try {
    await aiGenApi.removeTask(row.taskId)
    ElMessage.success('任务已删除')
    if (currentTask.value && currentTask.value.taskId === row.taskId) {
      currentTask.value = null
      questions.value = []
      quesFinalSyncedId = null
    }
    loadTasks(true, true)
  } catch (e) {
    // 拦截器已提示（生成中任务不可删除等）
  }
}

async function loadQuestions(taskId, silent = false) {
  if (!silent) questionsLoading.value = true
  try {
    questions.value = await aiGenApi.questions(taskId)
  } catch (e) {
    // 拦截器已提示
  } finally {
    if (!silent) questionsLoading.value = false
  }
}

// ---------- 查看 / 编辑 ----------
const dialog = reactive({
  visible: false,
  mode: 'view',
  saving: false,
  questionId: null,
  form: { quesType: '', difficulty: 'MEDIUM', stem: '', options: [], analysis: '' }
})

function parseOptions(q) {
  try {
    return q.optionsJson ? JSON.parse(q.optionsJson) : []
  } catch (e) {
    return []
  }
}

function openView(row) {
  dialog.mode = 'view'
  dialog.questionId = row.id
  dialog.form = {
    quesType: row.quesType,
    difficulty: row.difficulty,
    stem: row.stem,
    options: parseOptions(row),
    analysis: row.analysis || ''
  }
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.questionId = row.id
  dialog.form = {
    quesType: row.quesType,
    difficulty: row.difficulty,
    stem: row.stem,
    options: parseOptions(row),
    analysis: row.analysis || ''
  }
  dialog.visible = true
}

async function saveEdit() {
  if (!dialog.form.stem || !dialog.form.stem.trim()) {
    ElMessage.warning('题干不能为空')
    return
  }
  dialog.saving = true
  try {
    await aiGenApi.updateQuestion(dialog.questionId, {
      stem: dialog.form.stem,
      difficulty: dialog.form.difficulty,
      analysis: dialog.form.analysis,
      options: dialog.form.options && dialog.form.options.length ? dialog.form.options : undefined
    })
    ElMessage.success('已保存')
    dialog.visible = false
    if (currentTask.value) loadQuestions(currentTask.value.taskId)
  } catch (e) {
    // 拦截器已提示
  } finally {
    dialog.saving = false
  }
}

// ---------- 采纳 / 丢弃 ----------
async function onAdopt(row) {
  try {
    await ElMessageBox.confirm('采纳后题目进入对应资料题库（自动去重），确定采纳吗？', '采纳确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    const itemId = await aiGenApi.adopt(row.id)
    ElMessage.success(`已采纳入库（题目id: ${itemId}）`)
    if (currentTask.value) loadQuestions(currentTask.value.taskId)
  } catch (e) {
    // 拦截器已提示（重复题等）
  }
}

async function onDiscard(row) {
  try {
    await ElMessageBox.confirm('丢弃后该题不再进入题库，确定丢弃吗？', '丢弃确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await aiGenApi.discard(row.id)
    ElMessage.success('已丢弃')
    if (currentTask.value) loadQuestions(currentTask.value.taskId)
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => {
  loadTasks(false)
})
onBeforeUnmount(() => {
  if (taskTimer) clearInterval(taskTimer)
  if (quesTimer) clearInterval(quesTimer)
})
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-weight: 600;
}
.opt-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.gen-spinner {
  margin-left: 4px;
  vertical-align: -2px;
}
</style>
