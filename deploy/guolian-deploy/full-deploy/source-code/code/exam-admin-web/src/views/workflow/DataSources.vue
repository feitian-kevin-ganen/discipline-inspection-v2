<template>
  <div class="page-container">
    <div class="page-card">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="数据源仅用于登记外部库连接并做连通性测试，系统不会对登记的库执行任意 SQL；表名白名单用于限定可见范围。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="名称 / JDBC 地址"
          clearable
          style="width: 240px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-button type="primary" @click="loadList">查询</el-button>
        <el-button type="primary" plain @click="openCreate">新增数据源</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="dbType" label="类型" width="100" align="center" />
        <el-table-column prop="jdbcUrl" label="JDBC 地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="120" show-overflow-tooltip />
        <el-table-column prop="allowedTables" label="表白名单" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="testRow(row)">测试连接</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无数据源" />

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑数据源' : '新增数据源'" width="680px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 业务库只读" />
        </el-form-item>
        <el-form-item label="数据库类型" required>
          <el-select v-model="form.dbType" style="width: 100%">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="Oracle" value="oracle" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC 地址" required>
          <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://10.64.16.14:4886/exam_db" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改' : ''"
          />
        </el-form-item>
        <el-form-item label="表白名单">
          <el-input v-model="form.allowedTables" placeholder="多张表用英文逗号分隔，留空表示不限制" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../../api'
import { fmtTime } from '../../utils/format'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ keyword: '', pageNum: 1, pageSize: 10 })
const editVisible = ref(false)
const form = reactive(emptyForm())

function emptyForm() {
  return {
    id: null,
    name: '',
    dbType: 'mysql',
    jdbcUrl: '',
    username: '',
    password: '',
    allowedTables: '',
    status: '0'
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await workflowApi.dblinkList({ ...query })
    list.value = data.records || data.list || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    dbType: row.dbType || 'mysql',
    jdbcUrl: row.jdbcUrl,
    username: row.username || '',
    password: '',
    allowedTables: row.allowedTables || '',
    status: row.status || '0'
  })
  editVisible.value = true
}

async function save() {
  if (!form.name.trim() || !form.jdbcUrl.trim()) {
    ElMessage.warning('名称与 JDBC 地址必填')
    return
  }
  saving.value = true
  try {
    await workflowApi.saveDblink({ ...form })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function testRow(row) {
  try {
    const res = await workflowApi.testDblink({ id: row.id })
    ElMessage.success(`连接成功：${res || 'OK'}`)
  } catch (e) {
    // 错误提示已由响应拦截器统一弹出
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', { type: 'warning' })
  await workflowApi.deleteDblink(row.id)
  ElMessage.success('已删除')
  loadList()
}

onMounted(loadList)
</script>
