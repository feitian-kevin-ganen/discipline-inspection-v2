<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span class="card-title">我的成绩</span></template>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="examName" label="测试名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="examTime" label="测试时间" width="170" />
        <el-table-column label="得分" width="130" align="center">
          <template #default="{ row }">
            <span class="score" :class="{ fail: !row.passed }">{{ row.myScore }}</span>
            <span class="text-secondary"> / {{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'" size="small">{{ row.grade || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否通过" width="90" align="center">
          <template #default="{ row }">{{ row.passed ? '通过' : '未通过' }}</template>
        </el-table-column>
        <el-table-column label="单位内排名" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.rankInOrg != null">第 {{ row.rankInOrg }} 名</span>
            <span v-else class="text-secondary">未公开</span>
          </template>
        </el-table-column>
        <el-table-column label="补考" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.retakeSeq > 0" size="small" type="warning">第{{ row.retakeSeq }}次补考</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="$router.push(`/score/${row.examId}`)">
              成绩详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无已发布的成绩" /></template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getMyTranscripts } from '@/api/take'

const loading = ref(false)
const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await getMyTranscripts({ pageNum: pageNum.value, pageSize })
    list.value = res.list || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.score {
  font-weight: 700;
  color: #67c23a;
}
.score.fail {
  color: #f56c6c;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
