<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span class="card-title">AI辅助生成题目</span>
          <el-button size="small" @click="$router.push('/ques/ai-gen/tasks')">生成中的题目列表</el-button>
        </div>
      </template>

      <el-form label-width="110px" class="form">
        <el-form-item label="资料分类">
          <el-cascader
            v-model="categoryPath"
            :options="categoryTree"
            :props="cascaderProps"
            clearable
            placeholder="全部分类（不选则列出全部资料）"
            style="width: 460px"
            @change="onCategoryChange"
          />
        </el-form-item>

        <el-form-item label="学习文件" required>
          <el-select
            v-model="materialIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择一个或多个学习资料（每个资料生成一个任务）"
            style="width: 460px"
            :loading="materialsLoading"
          >
            <el-option
              v-for="m in materials"
              :key="m.materialId"
              :label="`【${TYPE_NAME[m.materialType] || m.materialType}】${m.title}`"
              :value="m.materialId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题型及数量" required>
          <div class="count-grid">
            <div v-for="t in TYPES" :key="t.key" class="count-item">
              <span class="count-label">{{ t.name }}</span>
              <el-input-number v-model="typeCounts[t.key]" :min="0" :max="50" size="small" />
            </div>
          </div>
        </el-form-item>

        <el-form-item label="难度及数量">
          <div class="count-grid">
            <div v-for="d in DIFFICULTIES" :key="d.key" class="count-item">
              <span class="count-label">{{ d.name }}</span>
              <el-input-number v-model="difficultyCounts[d.key]" :min="0" :max="50" size="small" />
            </div>
          </div>
          <div class="tip">难度总数与题型总数不一致时，按 易→中→难 顺序分配，不足部分默认「中」。</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="onGenerate">
            AI智能题目生成
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon title="生成是异步的：提交后每个资料一个生成任务，可前往「生成中的题目列表」查看进度并采纳题目。" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiGenApi, libApi } from '../../api'

const router = useRouter()

const TYPES = [
  { key: 'SINGLE', name: '单选' },
  { key: 'MULTI', name: '多选' },
  { key: 'JUDGE', name: '判断' },
  { key: 'FILL', name: '填空' },
  { key: 'ESSAY', name: '简答' }
]
const DIFFICULTIES = [
  { key: 'EASY', name: '易' },
  { key: 'MEDIUM', name: '中' },
  { key: 'HARD', name: '难' }
]
const TYPE_NAME = { DOC: '文档', VIDEO: '视频', RICHTEXT: '图文' }

const categoryTree = ref([])
const cascaderProps = { value: 'catId', label: 'catName', children: 'children', checkStrictly: true, emitPath: true }
const categoryPath = ref(null)
const materials = ref([])
const materialsLoading = ref(false)
const materialIds = ref([])
const typeCounts = reactive({ SINGLE: 5, MULTI: 2, JUDGE: 3, FILL: 0, ESSAY: 0 })
const difficultyCounts = reactive({ EASY: 3, MEDIUM: 5, HARD: 2 })
const submitting = ref(false)

function currentCategoryId() {
  const path = categoryPath.value
  return Array.isArray(path) && path.length ? path[path.length - 1] : null
}

async function loadMaterials() {
  materialsLoading.value = true
  try {
    materials.value = await aiGenApi.materials(currentCategoryId())
    materialIds.value = materialIds.value.filter((id) => materials.value.some((m) => m.materialId === id))
  } catch (e) {
    // 拦截器已提示
  } finally {
    materialsLoading.value = false
  }
}

function onCategoryChange() {
  loadMaterials()
}

async function onGenerate() {
  if (!materialIds.value.length) {
    ElMessage.warning('请至少选择一个学习资料')
    return
  }
  const total = TYPES.reduce((s, t) => s + (typeCounts[t.key] || 0), 0)
  if (total <= 0) {
    ElMessage.warning('请设置题型数量（总量须大于 0）')
    return
  }
  submitting.value = true
  try {
    await aiGenApi.createTasks({
      materialIds: materialIds.value,
      typeCounts: { ...typeCounts },
      difficultyCounts: { ...difficultyCounts }
    })
    try {
      await ElMessageBox.confirm(
        '大模型辅助生成中，请稍后。您也可以点击确定去“生成中的题目列表”查看进度。',
        '已提交生成任务',
        { confirmButtonText: '去生成列表', cancelButtonText: '留在本页', type: 'info' }
      )
      router.push('/ques/ai-gen/tasks')
    } catch (e) {
      // 留在本页
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadMaterials()
  try {
    categoryTree.value = await libApi.categoryTree()
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.page {
  max-width: 900px;
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
.count-grid {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}
.count-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.count-label {
  font-size: 14px;
  color: #606266;
}
.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
  width: 100%;
}
</style>
