<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { basicUserApi } from '../../../api/basic'
import { copyText, fmtDateTime, REGEX } from '../../../utils/format'

/** 状态：1-正常 0-禁用 */
const STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '禁用' },
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  empNo: '',
  username: '',
  phone: '',
  status: undefined,
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicUserApi.list(query)
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
  query.empNo = ''
  query.username = ''
  query.phone = ''
  query.status = undefined
  handleSearch()
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户 ${row.username || row.empNo} 吗？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await basicUserApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  username: '',
  empNo: '',
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  admin: false,
  status: true,
})

// 邮箱选填：为空直接放行
const emailRule = {
  validator: (_rule, value, callback) => {
    if (!value) return callback()
    REGEX.email.test(value) ? callback() : callback(new Error('邮箱格式不正确'))
  },
  trigger: 'blur',
}

const rules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    {
      pattern: REGEX.username,
      message: '以字母开头，只允许字母、数字、下划线，3-32位',
      trigger: 'blur',
    },
  ],
  empNo: [
    { required: true, message: '请输入员工工号', trigger: 'blur' },
    { min: 3, max: 50, message: '长度 3-50 位', trigger: 'blur' },
  ],
  email: [emailRule],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    username: '',
    empNo: '',
    nickname: '',
    email: '',
    phone: '',
    avatar: '',
    admin: false,
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    username: row.username || '',
    empNo: row.empNo || '',
    nickname: row.nickname || '',
    email: row.email || '',
    phone: row.phone || '',
    avatar: row.avatar || '',
    admin: row.admin === 1,
    status: row.status !== 0,
  })
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        username: form.username,
        empNo: form.empNo,
        nickname: form.nickname || undefined,
        email: form.email || undefined,
        phone: form.phone || undefined,
        avatar: form.avatar || undefined,
        admin: form.admin ? 1 : 0,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await basicUserApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicUserApi.create(payload)
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
        <div class="pro-card-title">用户列表</div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="登录名">
          <el-input
            v-model="query.username"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="员工工号">
          <el-input
            v-model="query.empNo"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input
            v-model="query.phone"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="opt in STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
          <el-table-column label="登录名" min-width="130">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.username)">{{ row.username || '-' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nickname || '-' }}</template>
          </el-table-column>
          <el-table-column label="员工工号" min-width="120">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.empNo)">{{ row.empNo || '-' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '-' }}</template>
          </el-table-column>
          <el-table-column label="管理员" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.admin === 1 ? 'danger' : 'info'" size="small">
                {{ row.admin === 1 ? '管理员' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
                {{ row.status === 0 ? '禁用' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近登录" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
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
      :title="currentRow ? '编辑用户' : '新增用户'"
      size="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="登录名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="!!currentRow"
            placeholder="例如：zhangsan"
          />
        </el-form-item>
        <el-form-item label="员工工号" prop="empNo">
          <el-input
            v-model="form.empNo"
            :disabled="!!currentRow"
            placeholder="例如：E10086"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="例如：张三" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="头像地址" prop="avatar">
          <el-input v-model="form.avatar" placeholder="选填，头像 URL" />
        </el-form-item>
        <el-form-item label="管理员">
          <el-switch v-model="form.admin" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="正常" inactive-text="禁用" />
        </el-form-item>
        <el-form-item v-if="currentRow" label="UUID">
          <el-input :model-value="currentRow.uuid || '-'" disabled />
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
