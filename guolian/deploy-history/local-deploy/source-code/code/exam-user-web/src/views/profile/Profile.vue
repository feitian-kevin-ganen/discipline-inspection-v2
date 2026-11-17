<template>
  <div class="page" v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never" class="mb16">
          <template #header><span class="card-title">个人信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="姓名">{{ me?.name || user?.name }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ me?.jobNo || user?.jobNo }}</el-descriptions-item>
            <el-descriptions-item label="所属单位">{{ me?.orgName || user?.orgName }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="r in me?.roles || user?.roles || []" :key="r" size="small" class="mr8">
                {{ ROLE_NAME[r] || r }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never">
          <template #header><span class="card-title">修改密码</span></template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="form.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="onSubmit">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never" class="mb16">
          <template #header><span class="card-title">我的学习统计</span></template>
          <el-row :gutter="16" class="mb16">
            <el-col :span="8" class="stat">
              <div class="stat-value">{{ fmtDuration(stat.totalEffectiveSec) }}</div>
              <div class="stat-label">累计有效学习时长</div>
            </el-col>
            <el-col :span="8" class="stat">
              <div class="stat-value">{{ stat.materialStats?.completed ?? 0 }}</div>
              <div class="stat-label">已完成资料</div>
            </el-col>
            <el-col :span="8" class="stat">
              <div class="stat-value">{{ Math.round((stat.taskStats?.doneRate ?? 0) * 100) }}%</div>
              <div class="stat-label">任务完成率（{{ stat.taskStats?.done ?? 0 }}/{{ stat.taskStats?.total ?? 0 }}）</div>
            </el-col>
          </el-row>
          <div ref="pieRef" class="chart"></div>
        </el-card>
        <el-card shadow="never">
          <template #header><span class="card-title">各分类学习时长分布</span></template>
          <div ref="barRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { changePassword, getMe } from '@/api/auth'
import { getPersonalStat } from '@/api/learn'
import { useUserStore } from '@/stores/user'
import { fmtDuration, ROLE_NAME } from '@/utils'

const router = useRouter()
const store = useUserStore()
const user = store.user

const loading = ref(false)
const me = ref(null)
const stat = ref({})
const pieRef = ref()
const barRef = ref()
let pieChart = null
let barChart = null

const formRef = ref()
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateNew = (rule, value, callback) => {
  if (!/^(?=.*[A-Za-z])(?=.*\d)[\S]{8,32}$/.test(value || '')) {
    callback(new Error('须 8-32 位且同时包含字母和数字'))
  } else {
    callback()
  }
}
const validateConfirm = (rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validateNew, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onSubmit() {
  await formRef.value.validate()
  saving.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    store.logout()
    router.push('/login')
  } finally {
    saving.value = false
  }
}

function renderCharts() {
  const ms = stat.value.materialStats || {}
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      title: { text: '资料学习完成情况', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '52%'],
          data: [
            { name: '已完成', value: ms.completed || 0, itemStyle: { color: '#67c23a' } },
            { name: '学习中', value: ms.inProgress || 0, itemStyle: { color: '#e6a23c' } },
            { name: '未开始', value: ms.notStarted || 0, itemStyle: { color: '#c0c4cc' } }
          ]
        }
      ]
    })
  }
  const cats = stat.value.categoryDist || []
  if (barRef.value) {
    barChart = echarts.init(barRef.value)
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 20, top: 30, bottom: 60 },
      xAxis: { type: 'category', data: cats.map((c) => c.catName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: '分钟' },
      series: [
        {
          type: 'bar',
          name: '有效学习时长',
          barMaxWidth: 40,
          itemStyle: { color: '#409eff' },
          data: cats.map((c) => Math.round((c.effectiveSec || 0) / 60))
        }
      ]
    })
  }
}

function onResize() {
  pieChart && pieChart.resize()
  barChart && barChart.resize()
}

async function load() {
  loading.value = true
  try {
    const [m, s] = await Promise.all([getMe().catch(() => null), getPersonalStat().catch(() => ({}))])
    me.value = m
    stat.value = s || {}
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart && pieChart.dispose()
  barChart && barChart.dispose()
})
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.mr8 {
  margin-right: 8px;
}
.stat {
  text-align: center;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}
.stat-label {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.chart {
  height: 260px;
}
</style>
