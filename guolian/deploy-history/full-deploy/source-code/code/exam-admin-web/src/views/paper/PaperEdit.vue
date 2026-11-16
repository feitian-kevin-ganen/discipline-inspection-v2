<template>
  <div class="page-container">
    <el-alert
      v-if="isSealed"
      type="warning"
      :closable="false"
      title="已封存，仅可复制新版本"
      style="margin-bottom: 16px"
    />

    <!-- 基本信息 -->
    <div class="page-card" style="margin-bottom: 16px">
      <el-form :model="form" label-width="110px" :disabled="isSealed">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="试卷名称" required>
              <el-input v-model="form.paperName" placeholder="请输入试卷名称" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="试卷类型" required>
              <el-radio-group v-model="form.paperType">
                <el-radio-button v-for="(v, k) in PAPER_TYPES" :key="k" :value="k">{{ v.label }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="卷面总分" required>
              <el-input-number v-model="form.totalScore" :min="1" :max="1000" :precision="0" style="width: 100%" @blur="form.totalScore = snapToStep(form.totalScore, 1, 1, 1000)" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="及格分" required>
              <el-input-number v-model="form.passScore" :min="0" :max="form.totalScore" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="多选判分规则">
              <el-select v-model="form.multiScoreRule" style="width: 100%">
                <el-option v-for="(label, k) in MULTI_SCORE_RULES" :key="k" :label="label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.paperType === 'RANDOM'" :span="12">
            <el-form-item label="全考生同卷">
              <el-switch v-model="form.samePaperForAll" />
              <span style="margin-left: 8px; color: #909399; font-size: 12px">
                {{ form.samePaperForAll ? '首次抽题后缓存，全考生同一套卷' : '一人一卷，进入考试时生成' }}
              </span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- FIXED 固定卷选题 -->
    <div v-if="form.paperType === 'FIXED'" class="page-card" style="margin-bottom: 16px">
      <div class="toolbar">
        <span style="font-weight: 600">已选题目（{{ selectedQuestions.length }} 题）</span>
        <div class="spacer" />
        <el-button type="primary" :disabled="isSealed" @click="openPicker">选题</el-button>
      </div>
      <el-table :data="selectedQuestions" border>
        <el-table-column label="序号" width="110" align="center">
          <template #default="{ $index }">
            <span style="margin-right: 6px">{{ $index + 1 }}</span>
            <el-button link :disabled="isSealed || $index === 0" @click="moveQuestion($index, -1)"><el-icon><Top /></el-icon></el-button>
            <el-button link :disabled="isSealed || $index === selectedQuestions.length - 1" @click="moveQuestion($index, 1)"><el-icon><Bottom /></el-icon></el-button>
          </template>
        </el-table-column>
        <el-table-column prop="stemText" label="题干" min-width="280" show-overflow-tooltip />
        <el-table-column label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="QUES_TYPES[row.quesType]?.type" size="small">{{ QUES_TYPES[row.quesType]?.label || row.quesType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="DIFFICULTIES[row.difficulty]?.type" size="small">{{ DIFFICULTIES[row.difficulty]?.label || row.difficulty }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分值" width="150" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.score" :min="0.5" :max="form.totalScore" :step="0.5" size="small" :disabled="isSealed" style="width: 110px" @blur="row.score = snapToStep(row.score, 0.5, 0.5, form.totalScore)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="isSealed" @click="removeQuestion($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right" :style="{ color: fixedSumOk ? '#67c23a' : '#f56c6c' }">
        当前合计 {{ fixedScoreSum }} 分 / 卷面总分 {{ form.totalScore }} 分
        <span v-if="!fixedSumOk">（合计必须等于卷面总分）</span>
      </div>
    </div>

    <!-- RANDOM 随机策略 -->
    <div v-if="form.paperType === 'RANDOM'" class="page-card" style="margin-bottom: 16px">
      <div class="toolbar">
        <span style="font-weight: 600">随机抽题策略</span>
        <div class="spacer" />
        <el-button v-if="!isSealed" type="primary" @click="addStrategyRow">新增规则</el-button>
      </div>
      <el-table :data="form.randomStrategy" border>
        <el-table-column label="题型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.quesType" placeholder="题型" :disabled="strategyDisabled">
              <el-option v-for="(v, k) in QUES_TYPES" :key="k" :label="v.label" :value="k" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="题库分类（不限可留空）" min-width="200">
          <template #default="{ row }">
            <el-tree-select
              v-model="row.bankId"
              :data="bankTree"
              :props="{ label: 'bankName', children: 'children' }"
              node-key="bankId"
              check-strictly
              clearable
              placeholder="不限"
              :disabled="strategyDisabled"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="难度（不限可留空）" width="160">
          <template #default="{ row }">
            <el-select v-model="row.difficulty" clearable placeholder="不限" :disabled="strategyDisabled">
              <el-option v-for="(v, k) in DIFFICULTIES" :key="k" :label="v.label" :value="k" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="抽题数" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.count" :min="1" :max="999" :precision="0" :disabled="strategyDisabled" style="width: 110px" @blur="row.count = snapToStep(row.count, 1, 1, 999)" />
          </template>
        </el-table-column>
        <el-table-column label="每题分值" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.score" :min="0.5" :max="form.totalScore" :step="0.5" :disabled="strategyDisabled" style="width: 110px" @blur="row.score = snapToStep(row.score, 0.5, 0.5, form.totalScore)" />
          </template>
        </el-table-column>
        <el-table-column v-if="!strategyDisabled" label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.randomStrategy.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right" :style="{ color: randomSumOk ? '#67c23a' : '#f56c6c' }">
        预计题数 {{ randomCountSum }} 题，预计总分 {{ randomScoreSum }} 分 / 卷面总分 {{ form.totalScore }} 分
        <span v-if="!randomSumOk">（预计总分必须等于卷面总分）</span>
      </div>
    </div>

    <!-- 等级规则 -->
    <div class="page-card" style="margin-bottom: 16px">
      <div class="toolbar">
        <span style="font-weight: 600">等级规则</span>
        <span style="color: #909399; font-size: 12px">区间须连续且完整覆盖 [0, 卷面总分]，左闭右开（末段右闭）</span>
        <div class="spacer" />
        <el-button type="primary" :disabled="isSealed" @click="addGradeRow">新增等级</el-button>
      </div>
      <el-table :data="form.gradeRules" border>
        <el-table-column label="等级名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.gradeName" placeholder="如：优 / 良 / 中 / 差" :disabled="isSealed" maxlength="10" />
          </template>
        </el-table-column>
        <el-table-column label="最低分（含）" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.minScore"
              :min="0"
              :max="form.totalScore"
              :disabled="isSealed"
              style="width: 130px"
              @change="(v) => onGradeScoreChange(row, 'minScore', v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最高分" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.maxScore"
              :min="0"
              :max="form.totalScore"
              :disabled="isSealed"
              style="width: 130px"
              @change="(v) => onGradeScoreChange(row, 'maxScore', v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="isSealed" @click="form.gradeRules.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card" style="text-align: center">
      <el-button @click="router.push('/papers')">返回列表</el-button>
      <el-button v-if="!isSealed" type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </div>

    <!-- 选题弹窗：勾选后点「确定」才生效，「取消」放弃本次勾选 -->
    <el-dialog v-model="pickerVisible" title="选择题目" width="80%" top="4vh" destroy-on-close :close-on-click-modal="false">
      <div style="display: flex; gap: 16px; height: 60vh">
        <div style="width: 240px; overflow: auto; border-right: 1px solid #ebeef5; padding-right: 12px">
          <el-tree
            :data="bankTree"
            :props="{ label: 'bankName', children: 'children' }"
            node-key="bankId"
            highlight-current
            default-expand-all
            @node-click="handleBankClick"
          />
        </div>
        <div style="flex: 1; display: flex; flex-direction: column; overflow: hidden">
          <div class="toolbar">
            <el-select v-model="pickerQuery.quesType" placeholder="题型" clearable style="width: 120px" @change="handlePickerSearch">
              <el-option v-for="(v, k) in QUES_TYPES" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-select v-model="pickerQuery.difficulty" placeholder="难度" clearable style="width: 110px" @change="handlePickerSearch">
              <el-option v-for="(v, k) in DIFFICULTIES" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-input
              v-model="pickerQuery.keyword"
              placeholder="题干关键词"
              clearable
              style="width: 200px"
              @keyup.enter="handlePickerSearch"
              @clear="handlePickerSearch"
            />
            <el-button type="primary" @click="handlePickerSearch">查询</el-button>
          </div>
          <el-table
            ref="pickerTableRef"
            :data="pickerList"
            v-loading="pickerLoading"
            border
            row-key="itemId"
            style="flex: 1"
            @selection-change="onPickerSelectionChange"
          >
            <el-table-column type="selection" width="45" reserve-selection />
            <el-table-column prop="stemText" label="题干" min-width="260" show-overflow-tooltip />
            <el-table-column prop="bankName" label="题库" width="130" show-overflow-tooltip />
            <el-table-column label="题型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="QUES_TYPES[row.quesType]?.type" size="small">{{ QUES_TYPES[row.quesType]?.label || row.quesType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="难度" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="DIFFICULTIES[row.difficulty]?.type" size="small">{{ DIFFICULTIES[row.difficulty]?.label || row.difficulty }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="scoreSuggest" label="建议分值" width="90" align="center" />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="pickerQuery.pageNum"
              v-model:page-size="pickerQuery.pageSize"
              :total="pickerTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadPicker"
              @current-change="loadPicker"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <span style="float: left; color: #909399">本次已选 {{ pickerSelected.length }} 题</span>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPicker">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { paperApi, quesApi } from '../../api'
import { PAPER_TYPES, MULTI_SCORE_RULES, QUES_TYPES, DIFFICULTIES } from '../../utils/dict'
import { snapToStep } from '../../utils/number'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const paperId = route.params.id

const saving = ref(false)
const isSealed = ref(false)
const bankTree = ref([])

const defaultGradeRules = () => [
  { gradeName: '优', minScore: 90, maxScore: 100 },
  { gradeName: '良', minScore: 80, maxScore: 90 },
  { gradeName: '中', minScore: 60, maxScore: 80 },
  { gradeName: '差', minScore: 0, maxScore: 60 }
]

const form = reactive({
  paperName: '',
  paperType: 'FIXED',
  totalScore: 100,
  passScore: 60,
  multiScoreRule: 'HALF_ON_MISS',
  samePaperForAll: false,
  randomStrategy: [],
  gradeRules: defaultGradeRules()
})

// ---------- FIXED 已选题目 ----------
const selectedQuestions = ref([])

const fixedScoreSum = computed(() => selectedQuestions.value.reduce((s, q) => s + (Number(q.score) || 0), 0))
const fixedSumOk = computed(() => selectedQuestions.value.length > 0 && fixedScoreSum.value === form.totalScore)

const moveQuestion = (index, offset) => {
  const target = index + offset
  if (target < 0 || target >= selectedQuestions.value.length) return
  const arr = selectedQuestions.value
  ;[arr[index], arr[target]] = [arr[target], arr[index]]
}

const removeQuestion = (index) => {
  const [removed] = selectedQuestions.value.splice(index, 1)
  // 同步取消抽屉表格中的勾选
  const row = pickerList.value.find((r) => r.itemId === removed.itemId)
  if (row && pickerTableRef.value) pickerTableRef.value.toggleRowSelection(row, false)
}

// ---------- RANDOM 策略 ----------
const strategyDisabled = computed(() => isSealed.value)
const randomCountSum = computed(() => form.randomStrategy.reduce((s, r) => s + (Number(r.count) || 0), 0))
const randomScoreSum = computed(() => form.randomStrategy.reduce((s, r) => s + (Number(r.count) || 0) * (Number(r.score) || 0), 0))
const randomSumOk = computed(() => form.randomStrategy.length > 0 && randomScoreSum.value === form.totalScore)

const addStrategyRow = () => {
  form.randomStrategy.push({ quesType: 'SINGLE', bankId: null, difficulty: null, count: 5, score: 2 })
}

// ---------- 等级规则 ----------
const addGradeRow = () => {
  form.gradeRules.push({ gradeName: '', minScore: 0, maxScore: 0 })
}

// 分数被清空（删除后失焦）时自动补全默认值，避免保存时区间校验报错
const onGradeScoreChange = (row, field, val) => {
  if (val === null || val === undefined || Number.isNaN(Number(val))) {
    row[field] = field === 'minScore' ? 0 : form.totalScore
    ElMessage.warning('分数不能为空，已自动补全默认值')
  }
}

const stripHtml = (html) => {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || ''
}

// ---------- 选题弹窗（勾选暂存，确定才生效） ----------
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerList = ref([])
const pickerTotal = ref(0)
const pickerTableRef = ref()
// 弹窗内的暂存勾选，打开时从已选题初始化，确定后写回 selectedQuestions
const pickerSelected = ref([])
const pickerQuery = reactive({ bankId: null, quesType: '', difficulty: '', keyword: '', pageNum: 1, pageSize: 10 })

const openPicker = () => {
  pickerSelected.value = selectedQuestions.value.map((q) => ({ ...q }))
  pickerVisible.value = true
  pickerQuery.pageNum = 1
  loadPicker()
}

const confirmPicker = () => {
  selectedQuestions.value = pickerSelected.value.map((q) => ({ ...q }))
  pickerVisible.value = false
}

const handleBankClick = (node) => {
  pickerQuery.bankId = node.bankId
  handlePickerSearch()
}

const handlePickerSearch = () => {
  pickerQuery.pageNum = 1
  loadPicker()
}

const loadPicker = async () => {
  pickerLoading.value = true
  try {
    const params = { ...pickerQuery, status: 'ENABLED' }
    if (!params.bankId) delete params.bankId
    const data = await quesApi.page(params)
    pickerList.value = data.list || []
    pickerTotal.value = data.total || 0
    // 回显勾选状态（以弹窗暂存勾选为准）
    await nextTick()
    const selectedIds = new Set(pickerSelected.value.map((q) => q.itemId))
    pickerList.value.forEach((row) => {
      if (selectedIds.has(row.itemId)) pickerTableRef.value?.toggleRowSelection(row, true)
    })
  } finally {
    pickerLoading.value = false
  }
}

const onPickerSelectionChange = (selection) => {
  const selIds = new Set(selection.map((r) => r.itemId))
  // 新增勾选
  selection.forEach((row) => {
    if (!pickerSelected.value.some((q) => q.itemId === row.itemId)) {
      pickerSelected.value.push({
        itemId: row.itemId,
        stemText: row.stemText,
        quesType: row.quesType,
        difficulty: row.difficulty,
        score: row.scoreSuggest || 1
      })
    }
  })
  // 当前页被取消勾选的
  pickerList.value.forEach((row) => {
    if (!selIds.has(row.itemId)) {
      const idx = pickerSelected.value.findIndex((q) => q.itemId === row.itemId)
      if (idx !== -1) pickerSelected.value.splice(idx, 1)
    }
  })
}

// ---------- 校验与保存 ----------
const validateGradeRules = () => {
  const rules = form.gradeRules
  if (!rules.length) {
    ElMessage.error('请至少配置一条等级规则')
    return false
  }
  if (rules.some((r) => !r.gradeName || !r.gradeName.trim())) {
    ElMessage.error('等级名称不能为空')
    return false
  }
  const sorted = [...rules].sort((a, b) => a.minScore - b.minScore)
  if (sorted[0].minScore !== 0 || sorted[sorted.length - 1].maxScore !== form.totalScore) {
    ElMessage.error(`等级规则区间须完整覆盖 [0, ${form.totalScore}]（40005）`)
    return false
  }
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i].minScore !== sorted[i - 1].maxScore) {
      ElMessage.error(`等级规则区间须连续（左闭右开，末段右闭），「${sorted[i - 1].gradeName}」与「${sorted[i].gradeName}」之间存在断档或重叠（40005）`)
      return false
    }
  }
  return true
}

const handleSave = async () => {
  if (!form.paperName.trim()) {
    ElMessage.error('请输入试卷名称')
    return
  }
  if (form.passScore > form.totalScore) {
    ElMessage.error('及格分不能大于卷面总分')
    return
  }

  let questionsPayload = null
  if (form.paperType === 'FIXED') {
    if (!selectedQuestions.value.length) {
      ElMessage.error('固定卷至少选择一道题目')
      return
    }
    if (!fixedSumOk.value) {
      ElMessage.error(`已选题目合计 ${fixedScoreSum.value} 分，必须等于卷面总分 ${form.totalScore} 分（40003）`)
      return
    }
    questionsPayload = selectedQuestions.value.map((q, i) => ({ itemId: q.itemId, score: q.score, seq: i + 1 }))
  } else {
    if (!form.randomStrategy.length) {
      ElMessage.error('随机策略卷至少配置一条抽题规则')
      return
    }
    if (form.randomStrategy.some((r) => !r.quesType || !r.count || !r.score)) {
      ElMessage.error('抽题规则的题型、抽题数、每题分值均不能为空')
      return
    }
    if (form.randomStrategy.some((r) => !Number.isInteger(Number(r.count)))) {
      ElMessage.error('抽题数必须为整数')
      return
    }
    if (!randomSumOk.value) {
      ElMessage.error(`随机策略预计总分 ${randomScoreSum.value} 分，必须等于卷面总分 ${form.totalScore} 分（40003）`)
      return
    }
  }

  if (!validateGradeRules()) return

  const strategyPayload = () =>
    form.randomStrategy.map((r) => ({
      quesType: r.quesType,
      bankId: r.bankId ?? null,
      difficulty: r.difficulty || null,
      count: r.count,
      score: r.score
    }))

  saving.value = true
  try {
    const base = {
      paperName: form.paperName.trim(),
      paperType: form.paperType,
      totalScore: form.totalScore,
      passScore: form.passScore,
      multiScoreRule: form.multiScoreRule
    }
    const gradeRules = form.gradeRules.map((r) => ({
      gradeName: r.gradeName.trim(),
      minScore: r.minScore,
      maxScore: r.maxScore
    }))

    if (!isEdit.value) {
      const body = { ...base, gradeRules }
      if (form.paperType === 'FIXED') {
        body.questions = questionsPayload
      } else {
        body.samePaperForAll = form.samePaperForAll
        body.randomStrategy = strategyPayload()
      }
      await paperApi.create(body)
      ElMessage.success('创建成功')
    } else {
      // 分步保存：基础信息（随机卷随基础信息提交抽题策略） -> 选题（FIXED） -> 等级规则
      const updateBody = { ...base }
      if (form.paperType === 'RANDOM') {
        updateBody.samePaperForAll = form.samePaperForAll
        updateBody.randomStrategy = strategyPayload()
      }
      await paperApi.update(paperId, updateBody)
      if (form.paperType === 'FIXED') {
        await paperApi.setQuestions(paperId, questionsPayload)
      }
      await paperApi.setGradeRules(paperId, gradeRules)
      ElMessage.success('保存成功')
    }
    router.push('/papers')
  } finally {
    saving.value = false
  }
}

// ---------- 初始化 ----------
const loadBankTree = async () => {
  bankTree.value = (await quesApi.bankTree()) || []
}

const loadDetail = async () => {
  const data = await paperApi.detail(paperId)
  form.paperName = data.paperName
  form.paperType = data.paperType
  form.totalScore = data.totalScore
  form.passScore = data.passScore
  form.multiScoreRule = data.multiScoreRule || 'HALF_ON_MISS'
  form.samePaperForAll = !!data.samePaperForAll
  form.randomStrategy = (data.randomStrategy || []).map((r) => ({ ...r }))
  form.gradeRules = (data.gradeRules && data.gradeRules.length ? data.gradeRules : defaultGradeRules()).map((r) => ({ ...r }))
  isSealed.value = data.status === 'SEALED'
  if (data.paperType === 'FIXED') {
    selectedQuestions.value = (data.questions || [])
      .slice()
      .sort((a, b) => (a.seq || 0) - (b.seq || 0))
      .map((q) => ({
        itemId: q.itemId,
        stemText: q.stemText || stripHtml(q.stem),
        quesType: q.quesType,
        difficulty: q.difficulty,
        score: q.score
      }))
  }
}

onMounted(() => {
  loadBankTree()
  if (isEdit.value) loadDetail()
})
</script>
