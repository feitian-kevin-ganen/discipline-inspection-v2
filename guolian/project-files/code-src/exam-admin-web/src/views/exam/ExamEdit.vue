<template>
  <div class="page-container">
    <div class="page-card">
      <h3 style="margin-top: 0">{{ isEdit ? '编辑测试场次' : '创建测试场次' }}</h3>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width: 720px" v-loading="loading">
        <el-form-item label="测试名称" prop="examName">
          <el-input v-model="form.examName" placeholder="请输入测试名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="关联试卷" prop="paperId">
          <el-select v-model="form.paperId" placeholder="请选择试卷" filterable :disabled="isEdit" style="width: 100%">
            <el-option v-for="p in papers" :key="p.paperId" :label="paperLabel(p)" :value="p.paperId" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试时间窗口" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开考时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="测试时长" prop="durationMin">
          <el-input-number v-model="form.durationMin" :min="1" :max="1440" />
          <span class="tip">分钟，从考生进入起算，且不超过窗口结束时间</span>
        </el-form-item>
      </el-form>

      <el-card shadow="never" style="max-width: 720px; margin-bottom: 16px">
        <template #header>测试规则</template>
        <el-form :model="form" label-width="160px">
          <el-form-item label="允许迟到进入时限">
            <el-input-number v-model="form.lateEnterMin" :min="0" :max="1440" />
            <span class="tip">分钟，相对开考时间；0 表示不允许迟到（开考后 1 分钟内可进入）</span>
          </el-form-item>
          <el-form-item label="切屏次数上限">
            <el-input-number v-model="form.switchScreenLimit" :min="0" :max="100" />
            <span class="tip">次，留空则取系统参数</span>
          </el-form-item>
          <el-form-item label="允许提前交卷">
            <el-switch v-model="form.allowEarlySubmit" />
          </el-form-item>
          <el-form-item v-if="form.allowEarlySubmit" label="提前交卷限制">
            <el-input-number v-model="form.earlySubmitAfterMin" :min="0" :max="1440" />
            <span class="tip">开考多少分钟后允许提前交卷</span>
          </el-form-item>
          <el-form-item label="题目顺序乱序">
            <el-switch v-model="form.shuffleQuestion" />
          </el-form-item>
          <el-form-item label="选项顺序乱序">
            <el-switch v-model="form.shuffleOption" />
          </el-form-item>
          <el-form-item label="测试须知">
            <el-input v-model="form.notice" type="textarea" :rows="4" placeholder="向考生展示的测试须知" />
          </el-form-item>
        </el-form>
      </el-card>

      <div style="max-width: 720px; padding-left: 120px">
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { examApi, paperApi } from '../../api'
import { PAPER_TYPES } from '../../utils/dict'

const route = useRoute()
const router = useRouter()
const examId = computed(() => route.params.id)
const isEdit = computed(() => !!examId.value)

const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const papers = ref([])

const form = reactive({
  examName: '',
  paperId: null,
  timeRange: [],
  durationMin: 120,
  lateEnterMin: 0,
  switchScreenLimit: 3,
  allowEarlySubmit: true,
  earlySubmitAfterMin: 0,
  shuffleQuestion: true,
  shuffleOption: true,
  notice: ''
})

const rules = {
  examName: [{ required: true, message: '请输入测试名称', trigger: 'blur' }],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  timeRange: [{ required: true, type: 'array', len: 2, message: '请选择测试时间窗口', trigger: 'change' }],
  durationMin: [{ required: true, message: '请设置测试时长', trigger: 'blur' }]
}

function paperLabel(p) {
  const t = PAPER_TYPES[p.paperType]
  return `${p.paperName}（${t ? t.label : p.paperType} ${p.totalScore}分）`
}

async function loadPapers() {
  const data = await paperApi.page({ pageNum: 1, pageSize: 200 })
  papers.value = data.list || []
}

async function loadDetail() {
  loading.value = true
  try {
    const d = await examApi.detail(examId.value)
    Object.assign(form, {
      examName: d.examName,
      paperId: d.paperId,
      timeRange: d.startTime && d.endTime ? [d.startTime, d.endTime] : [],
      durationMin: d.durationMin,
      lateEnterMin: d.lateEnterMin ?? 0,
      switchScreenLimit: d.switchScreenLimit ?? 3,
      allowEarlySubmit: d.allowEarlySubmit ?? true,
      earlySubmitAfterMin: d.earlySubmitAfterMin ?? 0,
      shuffleQuestion: d.shuffleQuestion ?? true,
      shuffleOption: d.shuffleOption ?? true,
      notice: d.notice || ''
    })
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  const { timeRange, ...rest } = form
  return {
    ...rest,
    startTime: timeRange[0],
    endTime: timeRange[1]
  }
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await examApi.update(examId.value, payload)
      ElMessage.success('保存成功')
      router.push(`/exams/${examId.value}`)
    } else {
      const data = await examApi.create(payload)
      ElMessage.success('创建成功')
      router.push(`/exams/${data.examId}`)
    }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadPapers()
  if (isEdit.value) await loadDetail()
})
</script>

<style scoped>
.tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
