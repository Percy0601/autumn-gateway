<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import { copyText, fmtDateTime, REGEX } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  appid: '',
  name: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await http.get('/api/system/app', { params: query })
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
  query.appid = ''
  query.name = ''
  handleSearch()
}

async function changeStatus(row) {
  const disable = row.status === 1
  try {
    await ElMessageBox.confirm(
      disable ? '确定禁用？' : '确定启用？',
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await http.put(`/api/system/app/${row.id}/${disable ? 'disable' : 'enable'}`)
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  appid: '',
  name: '',
  basePath: '',
  description: '',
  status: true,
})

const rules = {
  appid: [
    { required: true, message: '请输入应用标识', trigger: 'blur' },
    { pattern: REGEX.appid, message: '以小写字母开头，3-32位', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  basePath: [
    {
      pattern: REGEX.basePath,
      message: '必须以 / 开头，例如 /api/order',
      trigger: 'blur',
    },
  ],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    appid: '',
    name: '',
    basePath: '',
    description: '',
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    appid: row.appid,
    name: row.name,
    basePath: row.basePath || '',
    description: row.description || '',
    status: row.status === 1,
  })
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        ...form,
        basePath: form.basePath || undefined,
        description: form.description || undefined,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await http.put(`/api/system/app/${currentRow.value.id}`, payload)
        ElMessage.success('更新成功')
      } else {
        await http.post('/api/system/app', payload)
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
        <div class="pro-card-title">应用列表</div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增应用</el-button>
        </div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="应用标识">
          <el-input
            v-model="query.appid"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="应用名称">
          <el-input
            v-model="query.name"
            placeholder="请输入"
            clearable
            style="width: 220px"
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
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="应用标识" min-width="120">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.appid)">{{ row.appid }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="应用名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="basePath" label="API 前缀" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.basePath || '-' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-popconfirm
                :title="row.status === 1 ? '确定禁用？' : '确定启用？'"
                width="160"
                @confirm="changeStatus(row)"
              >
                <template #reference>
                  <el-link :class="row.status === 1 ? 'link-op disabled-link' : 'link-op enabled-link'">
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </el-link>
                </template>
              </el-popconfirm>
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
      :title="currentRow ? '编辑应用' : '新增应用'"
      size="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="应用标识" prop="appid">
          <el-input
            v-model="form.appid"
            :disabled="!!currentRow"
            placeholder="例如：order"
          />
        </el-form-item>
        <el-form-item label="应用名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：订单系统" />
        </el-form-item>
        <el-form-item label="API 前缀" prop="basePath">
          <el-input v-model="form.basePath" placeholder="例如：/api/order" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            active-text="启用"
            inactive-text="禁用"
          />
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
