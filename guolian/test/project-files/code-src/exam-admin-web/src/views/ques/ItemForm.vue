<template>
  <div class="page-container">
    <div class="page-card" v-loading="loading">
      <!-- 基本信息 -->
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属分类" required>
          <el-tree-select
            v-model="form.bankId"
            :data="bankTree"
            :props="{ label: 'bankName', value: 'bankId', children: 'children' }"
            check-strictly
            placeholder="请选择题库分类"
            style="width: 320px"
          />
        </el-form-item>
        <el-form-item label="题型" required>
          <el-radio-group v-model="form.quesType" :disabled="isEdit" @change="onTypeChange">
            <el-radio v-for="(v, k) in QUES_TYPES" :key="k" :value="k">{{ v.label }}</el-radio>
          </el-radio-group>
          <span v-if="isEdit" class="form-tip">已有题目不可切换题型</span>
        </el-form-item>
        <el-form-item label="难度" required>
          <el-radio-group v-model="form.difficulty">
            <el-radio v-for="(v, k) in DIFFICULTIES" :key="k" :value="k">{{ v.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="建议分值">
          <el-input-number v-model="form.scoreSuggest" :min="0" :max="100" :precision="1" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车创建标签"
            style="width: 420px"
          >
            <el-option v-for="t in form.tags" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" placeholder="如：党纪处分条例第三章" style="width: 420px" />
        </el-form-item>

        <!-- 题干 -->
        <el-form-item label="题干" required>
          <div style="flex: 1">
            <el-input v-model="form.stem" type="textarea" :rows="4" placeholder="请输入题干" />
            <div class="form-tip">支持粘贴含图片 URL 的 HTML 富文本内容</div>
          </div>
        </el-form-item>

        <!-- 选择题选项 -->
        <template v-if="form.quesType === 'SINGLE' || form.quesType === 'MULTI'">
          <el-form-item v-for="(opt, idx) in form.options" :key="idx" :label="`选项 ${opt.optKey}`">
            <div class="opt-row">
              <el-radio
                v-if="form.quesType === 'SINGLE'"
                v-model="singleAnswer"
                :value="opt.optKey"
                class="opt-mark"
              >答案</el-radio>
              <el-checkbox v-else v-model="opt.isAnswer" class="opt-mark">答案</el-checkbox>
              <el-input v-model="opt.content" placeholder="选项内容" style="flex: 1" />
              <el-button link type="danger" :disabled="form.options.length <= 2" @click="removeOption(idx)">删除</el-button>
            </div>
          </el-form-item>
          <el-form-item label=" ">
            <el-button plain size="small" @click="addOption">添加选项</el-button>
          </el-form-item>
        </template>

        <!-- 判断题答案 -->
        <el-form-item v-if="form.quesType === 'JUDGE'" label="答案" required>
          <el-radio-group v-model="judgeAnswer">
            <el-radio value="T">正确</el-radio>
            <el-radio value="F">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 填空题答案 -->
        <template v-if="form.quesType === 'FILL'">
          <el-form-item v-for="(blank, idx) in fillBlanks" :key="idx" :label="`第 ${idx + 1} 空`">
            <div class="opt-row">
              <el-select
                v-model="blank.answers"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入等价答案，回车添加"
                style="flex: 1"
              >
                <el-option v-for="a in blank.answers" :key="a" :label="a" :value="a" />
              </el-select>
              <el-button link type="danger" :disabled="fillBlanks.length <= 1" @click="fillBlanks.splice(idx, 1)">删除</el-button>
            </div>
          </el-form-item>
          <el-form-item label=" ">
            <el-button plain size="small" @click="fillBlanks.push({ answers: [] })">添加空</el-button>
          </el-form-item>
          <el-form-item label="判分配置">
            <el-checkbox v-model="form.fillConfig.ignoreCase">忽略大小写</el-checkbox>
            <el-checkbox v-model="form.fillConfig.ignoreWidth">忽略全半角</el-checkbox>
            <el-checkbox v-model="form.fillConfig.blankOrdered">多空按顺序</el-checkbox>
          </el-form-item>
        </template>

        <!-- 简答题答案 -->
        <template v-if="form.quesType === 'ESSAY'">
          <el-form-item label="参考答案">
            <el-input v-model="essayReference" type="textarea" :rows="4" placeholder="请输入参考答案" />
          </el-form-item>
          <el-form-item label="评分要点">
            <el-select
              v-model="essayKeyPoints"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入评分要点，回车添加"
              style="width: 420px"
            >
              <el-option v-for="p in essayKeyPoints" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 解析 -->
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" placeholder="请输入答案解析（支持富文本）" />
        </el-form-item>

        <!-- 查重 -->
        <el-form-item label=" ">
          <el-button plain :loading="dupLoading" @click="onDupCheck">查重</el-button>
        </el-form-item>
        <el-form-item v-if="dupResults.length" label="查重结果">
          <div style="flex: 1">
            <el-alert
              v-for="d in dupResults"
              :key="d.itemId"
              type="warning"
              :closable="false"
              style="margin-bottom: 8px"
              :title="`相似度 ${(d.similarity * 100).toFixed(0)}%：${d.stemText}`"
            />
          </div>
        </el-form-item>
        <el-form-item v-else-if="dupChecked" label="查重结果">
          <el-alert type="success" :closable="false" title="未发现疑似重复题目" />
        </el-form-item>

        <!-- 提交 -->
        <el-form-item label=" ">
          <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
          <el-button @click="router.push('/ques/items')">返回列表</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quesApi } from '../../api'
import { QUES_TYPES, DIFFICULTIES } from '../../utils/dict'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const loading = ref(false)
const submitting = ref(false)
const bankTree = ref([])

const form = reactive({
  bankId: null,
  quesType: 'SINGLE',
  difficulty: 'MEDIUM',
  scoreSuggest: 5,
  tags: [],
  source: '',
  stem: '',
  options: [],
  analysis: '',
  fillConfig: { ignoreCase: true, ignoreWidth: true, blankOrdered: false }
})

// 各题型答案的中间状态
const singleAnswer = ref('')
const judgeAnswer = ref('T')
const fillBlanks = ref([{ answers: [] }])
const essayReference = ref('')
const essayKeyPoints = ref([])

const OPT_KEYS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

const defaultOptions = () => [
  { optKey: 'A', content: '', isAnswer: false },
  { optKey: 'B', content: '', isAnswer: false }
]

const resetAnswerArea = () => {
  form.options = defaultOptions()
  singleAnswer.value = ''
  judgeAnswer.value = 'T'
  fillBlanks.value = [{ answers: [] }]
  essayReference.value = ''
  essayKeyPoints.value = []
  form.fillConfig = { ignoreCase: true, ignoreWidth: true, blankOrdered: false }
}

const onTypeChange = () => {
  resetAnswerArea()
  dupResults.value = []
  dupChecked.value = false
}

const addOption = () => {
  if (form.options.length >= OPT_KEYS.length) return
  form.options.push({ optKey: OPT_KEYS[form.options.length], content: '', isAnswer: false })
}

const removeOption = (idx) => {
  const removed = form.options[idx]
  form.options.splice(idx, 1)
  form.options.forEach((o, i) => (o.optKey = OPT_KEYS[i]))
  if (singleAnswer.value === removed.optKey) singleAnswer.value = ''
}

// ---------- 查重 ----------
const dupLoading = ref(false)
const dupChecked = ref(false)
const dupResults = ref([])

const onDupCheck = async () => {
  if (!form.stem?.trim()) {
    ElMessage.warning('请先输入题干')
    return
  }
  dupLoading.value = true
  try {
    dupResults.value = (await quesApi.dupCheck({
      stem: form.stem,
      quesType: form.quesType,
      excludeItemId: isEdit.value ? Number(route.params.id) : undefined
    })) || []
    dupChecked.value = true
  } finally {
    dupLoading.value = false
  }
}

// ---------- 提交 ----------
const buildBody = () => {
  const body = {
    bankId: form.bankId,
    quesType: form.quesType,
    stem: form.stem,
    analysis: form.analysis || undefined,
    difficulty: form.difficulty,
    tags: form.tags.length ? form.tags : undefined,
    scoreSuggest: form.scoreSuggest,
    source: form.source || undefined
  }
  if (form.quesType === 'SINGLE' || form.quesType === 'MULTI') {
    body.options = form.options.map((o) => ({
      optKey: o.optKey,
      content: o.content,
      isAnswer: form.quesType === 'SINGLE' ? o.optKey === singleAnswer.value : !!o.isAnswer
    }))
  } else if (form.quesType === 'JUDGE') {
    body.answer = { value: judgeAnswer.value }
  } else if (form.quesType === 'FILL') {
    body.answer = {
      blanks: fillBlanks.value.map((b, i) => ({ no: i + 1, answers: b.answers, ordered: false }))
    }
    body.fillConfig = { ...form.fillConfig }
  } else if (form.quesType === 'ESSAY') {
    body.answer = { reference: essayReference.value, keyPoints: essayKeyPoints.value }
  }
  return body
}

const validate = () => {
  if (!form.bankId) return '请选择所属分类'
  if (!form.stem?.trim()) return '请输入题干'
  if (form.quesType === 'SINGLE' || form.quesType === 'MULTI') {
    if (form.options.length < 2) return '选项至少 2 项'
    if (form.options.some((o) => !o.content?.trim())) return '请填写全部选项内容'
    if (form.quesType === 'SINGLE') {
      if (!singleAnswer.value) return '请选择正确答案'
    } else if (!form.options.some((o) => o.isAnswer)) {
      return '请至少勾选一个正确答案'
    }
  }
  if (form.quesType === 'FILL') {
    if (!fillBlanks.value.length || fillBlanks.value.some((b) => !b.answers.length)) return '请为每个空配置至少一个答案'
  }
  return null
}

const onSubmit = async () => {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  submitting.value = true
  try {
    const body = buildBody()
    const res = isEdit.value
      ? await quesApi.update(route.params.id, body)
      : await quesApi.create(body)
    const dupHints = res?.dupHints || []
    if (dupHints.length) {
      await ElMessageBox.alert(
        dupHints.map((d) => `相似度 ${(d.similarity * 100).toFixed(0)}%：${d.stemText}`).join('\n'),
        '保存成功，但发现疑似重复题目',
        { type: 'warning', confirmButtonText: '知道了' }
      )
    } else {
      ElMessage.success('保存成功')
    }
    router.push('/ques/items')
  } finally {
    submitting.value = false
  }
}

// ---------- 编辑回显 ----------
const loadDetail = async () => {
  loading.value = true
  try {
    const d = await quesApi.detail(route.params.id)
    form.bankId = d.bankId
    form.quesType = d.quesType
    form.difficulty = d.difficulty
    form.scoreSuggest = d.scoreSuggest ?? 5
    form.tags = d.tags || []
    form.source = d.source || ''
    form.stem = d.stem || ''
    form.analysis = d.analysis || ''
    form.fillConfig = d.fillConfig || { ignoreCase: true, ignoreWidth: true, blankOrdered: false }
    if (d.quesType === 'SINGLE' || d.quesType === 'MULTI') {
      form.options = d.options || []
      if (d.quesType === 'SINGLE') {
        singleAnswer.value = (d.options || []).find((o) => o.isAnswer)?.optKey || ''
      }
    } else if (d.quesType === 'JUDGE') {
      judgeAnswer.value = d.answer?.value || 'T'
    } else if (d.quesType === 'FILL') {
      fillBlanks.value = (d.answer?.blanks || []).map((b) => ({ answers: b.answers || [] }))
      if (!fillBlanks.value.length) fillBlanks.value = [{ answers: [] }]
    } else if (d.quesType === 'ESSAY') {
      essayReference.value = d.answer?.reference || ''
      essayKeyPoints.value = d.answer?.keyPoints || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  bankTree.value = (await quesApi.bankTree()) || []
  if (isEdit.value) {
    await loadDetail()
  } else {
    resetAnswerArea()
  }
})
</script>

<style scoped>
.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.opt-mark {
  flex-shrink: 0;
  margin-right: 0;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
div.form-tip {
  margin-left: 0;
  margin-top: 4px;
}
</style>
