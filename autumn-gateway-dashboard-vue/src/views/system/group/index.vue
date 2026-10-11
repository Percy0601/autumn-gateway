<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { basicGroupApi } from '../../../api/basic'
import { fmtDateTime } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  name: '',
  code: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicGroupApi.list(query)
    rows.value = body.data || []
    total.value = body.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadData()
}

function handleReset() {
  query.name = ''
  query.code = ''
  handleSearch()
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户组「${row.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await basicGroupApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  name: '',
  code: '',
  orgId: '',
  jobId: '',
})

const rules = {
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, { name: '', code: '', orgId: '', jobId: '' })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    name: row.name,
    code: row.code,
    orgId: row.orgId ?? '',
    jobId: row.jobId ?? '',
  })
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        name: form.name,
        code: form.code,
        orgId: Number(form.orgId) || 0,
        jobId: Number(form.jobId) || 0,
      }
      if (currentRow.value) {
        await basicGroupApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicGroupApi.create(payload)
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

onMounted(loadData)
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">用户组列表</div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户组</el-button>
        </div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="岗位名称">
          <el-input
            v-model="query.name"
            placeholder="请输入"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="岗位编码">
          <el-input
            v-model="query.code"
            placeholder="请输入"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="code" label="岗位编码" min-width="140" show-overflow-tooltip />
          <el-table-column label="部门ID" width="100" align="center">
            <template #default="{ row }">{{ row.orgId || '-' }}</template>
          </el-table-column>
          <el-table-column label="岗位ID" width="100" align="center">
            <template #default="{ row }">{{ row.jobId || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link type="danger" class="link-op" @click="handleRemove(row)">删除</el-link>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pro-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="drawerOpen"
      :title="currentRow ? '编辑用户组' : '新增用户组'"
      size="440px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：研发工程师" />
        </el-form-item>
        <el-form-item label="岗位编码" prop="code">
          <el-input v-model="form.code" placeholder="例如：RD_ENGINEER" />
        </el-form-item>
        <el-form-item label="部门ID">
          <el-input-number v-model="form.orgId" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="岗位ID">
          <el-input-number v-model="form.jobId" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="pro-drawer-footer">
          <el-button @click="drawerOpen = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
