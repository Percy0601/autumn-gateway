<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import { copyText, fmtDateTime, REGEX } from '../../../utils/format'

const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const applications = ref([])

const query = reactive({
  current: 1,
  pageSize: 10,
  appId: undefined,
  code: '',
  name: '',
})

// 应用下拉选项：名称 (标识)
const appOptions = ref([])

async function loadApps() {
  try {
    const body = await http.get('/api/system/app/list')
    applications.value = body.data || []
    appOptions.value = applications.value.map((app) => ({
      label: `${app.name} (${app.appid})`,
      value: app.id,
    }))
  } catch {
    applications.value = []
    appOptions.value = []
  }
}

function appName(appId) {
  const app = applications.value.find((a) => a.id === appId)
  return app ? `${app.name} (${app.appid})` : '-'
}

async function loadData() {
  loading.value = true
  try {
    const body = await http.get('/api/system/role', { params: query })
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
  query.appId = undefined
  query.code = ''
  query.name = ''
  handleSearch()
}

function goDetail(row) {
  router.push(`/system/role/detail/${row.id}`)
}

function goTab(row, tab) {
  router.push({ path: `/system/role/detail/${row.id}`, query: { tab } })
}

async function changeStatus(row) {
  const disable = row.status === 1
  try {
    await ElMessageBox.confirm(
      disable ? '确定禁用该角色？' : '确定启用该角色？',
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await http.put(`/api/system/role/${row.id}/${disable ? 'disable' : 'enable'}`)
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  appId: undefined,
  code: '',
  name: '',
  description: '',
  status: true,
})

const rules = {
  appId: [{ required: true, message: '请选择所属应用', trigger: 'change' }],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    {
      pattern: REGEX.username,
      message: '以字母开头，只允许字母、数字、下划线，3-32位',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    appId: undefined,
    code: '',
    name: '',
    description: '',
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    appId: row.appId,
    code: row.code,
    name: row.name,
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
        description: form.description || undefined,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await http.put(`/api/system/role/${currentRow.value.id}`, payload)
        ElMessage.success('更新成功')
      } else {
        await http.post('/api/system/role', payload)
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

onMounted(() => {
  loadApps()
  loadData()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">角色列表</div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
        </div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="所属应用">
          <el-select
            v-model="query.appId"
            placeholder="全部"
            clearable
            filterable
            style="width: 220px"
          >
            <el-option
              v-for="opt in appOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input
            v-model="query.code"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input
            v-model="query.name"
            placeholder="请输入"
            clearable
            style="width: 180px"
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
          <el-table-column label="角色编码" min-width="130">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="角色名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="所属应用" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ appName(row.appId) }}</template>
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
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="goDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="goTab(row, 'permissions')">
                分配权限
              </el-link>
              <el-link type="primary" class="link-op" @click="goTab(row, 'users')">分配用户</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-popconfirm
                :title="row.status === 1 ? '确定禁用该角色？' : '确定启用该角色？'"
                width="170"
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
      :title="currentRow ? '编辑角色' : '新增角色'"
      size="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属应用" prop="appId">
          <el-select
            v-model="form.appId"
            placeholder="请选择应用"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="opt in appOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!currentRow"
            placeholder="例如：admin"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：管理员" />
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
          <el-switch v-model="form.status" active-text="启用" inactive-text="禁用" />
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
