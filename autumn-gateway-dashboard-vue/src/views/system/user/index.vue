<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Key, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import { copyText, fmtDateTime, REGEX } from '../../../utils/format'

const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  username: '',
  nickname: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await http.get('/api/system/user', { params: query })
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
  query.username = ''
  query.nickname = ''
  handleSearch()
}

function goDetail(row) {
  router.push(`/system/user/detail/${row.id}`)
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
  await http.put(`/api/system/user/${row.id}/${disable ? 'disable' : 'enable'}`)
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  username: '',
  nickname: '',
  avatar: '',
  email: '',
  phone: '',
  empNo: '',
  status: true,
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: REGEX.username,
      message: '以字母开头，只允许字母、数字、下划线，3-32位',
      trigger: 'blur',
    },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    username: '',
    nickname: '',
    avatar: '',
    email: '',
    phone: '',
    empNo: '',
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    username: row.username,
    nickname: row.nickname || '',
    avatar: row.avatar || '',
    email: row.email || '',
    phone: row.phone || '',
    empNo: row.empNo || '',
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
        nickname: form.nickname || undefined,
        avatar: form.avatar || undefined,
        email: form.email || undefined,
        phone: form.phone || undefined,
        empNo: form.empNo || undefined,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await http.put(`/api/system/user/${currentRow.value.id}`, payload)
        ElMessage.success('更新成功')
      } else {
        await http.post('/api/system/user', payload)
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

// ---------- 重置密码 ----------
const pwdModalOpen = ref(false)
const pwdTarget = ref(null)
const newPassword = ref('')
const resetting = ref(false)

function openPwdModal(row) {
  pwdTarget.value = row
  newPassword.value = ''
  pwdModalOpen.value = true
}

async function handleResetPassword() {
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('密码长度至少6位')
    return
  }
  resetting.value = true
  try {
    await http.put(`/api/system/user/${pwdTarget.value.id}/reset-password`, {
      password: newPassword.value,
    })
    ElMessage.success(`已重置 ${pwdTarget.value.username} 的密码`)
    pwdModalOpen.value = false
  } catch {
    ElMessage.error('重置密码失败')
  } finally {
    resetting.value = false
  }
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
        <el-form-item label="用户名">
          <el-input
            v-model="query.username"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input
            v-model="query.nickname"
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
          <el-table-column label="用户名" min-width="120">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.username)">{{ row.username }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="昵称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="nickname-cell">
                <el-avatar :size="24" :src="row.avatar">
                  <el-icon><UserFilled /></el-icon>
                </el-avatar>
                <span>{{ row.nickname || '-' }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '-' }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="手机" min-width="120">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column prop="empNo" label="员工号" min-width="100">
            <template #default="{ row }">{{ row.empNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="goDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link class="link-op orange-link" :icon="Key" @click="openPwdModal(row)">
                重置密码
              </el-link>
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
      :title="currentRow ? '编辑用户' : '新增用户'"
      size="450px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="!!currentRow"
            placeholder="例如：admin"
          />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="例如：管理员" />
        </el-form-item>
        <el-form-item label="头像 URL">
          <el-input v-model="form.avatar" placeholder="https://example.com/avatar.png" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="admin@example.com" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="13800138000" />
        </el-form-item>
        <el-form-item label="员工号">
          <el-input v-model="form.empNo" placeholder="EMP-001" />
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

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="pwdModalOpen"
      :title="`重置密码 — ${pwdTarget?.username || ''}`"
      width="420px"
      :close-on-click-modal="false"
    >
      <div style="margin-bottom: 8px">
        为用户 <strong>{{ pwdTarget?.username }}</strong> 设置新密码：
      </div>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        placeholder="请输入新密码（至少6位）"
        @keyup.enter="handleResetPassword"
      />
      <template #footer>
        <el-button @click="pwdModalOpen = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="handleResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.nickname-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.orange-link {
  color: #fa8c16;
}
</style>
