<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicGroupApi, basicNamespaceApi, basicRoleApi, basicUserApi } from '../../../api/basic'
import { copyText, fmtDateTime, REGEX, roleTypeLabel } from '../../../utils/format'
import { isConfirmed } from '../../../utils/confirm'
import RelationDetailDialog from '../../../components/RelationDetailDialog.vue'

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

// 用户所属用户组：列表接口不返回，需按行拉取（一般一个用户只属于一个用户组）
const groupMap = ref({})

async function loadGroupNames(list) {
  await Promise.all(
    list.map(async (r) => {
      try {
        const body = await basicUserApi.groups(r.id)
        const arr = body?.data || []
        groupMap.value[r.id] = arr.map((g) => g.name).filter(Boolean)
      } catch {
        groupMap.value[r.id] = []
      }
    }),
  )
}

async function loadData() {
  loading.value = true
  try {
    const body = await basicUserApi.list(query)
    rows.value = body.data || []
    total.value = body.total || 0
    await loadGroupNames(rows.value)
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

// ---------- 批量删除 ----------
const tableRef = ref()
const selectedRows = ref([])

function handleSelectionChange(val) {
  selectedRows.value = val
}

async function handleBatchRemove() {
  if (!selectedRows.value.length) return
  const names = selectedRows.value.map((r) => r.username || r.empNo).join('、')
  const ok = await isConfirmed(
    `此操作将删除选中的 ${selectedRows.value.length} 个用户（${names}），是否继续？`,
  )
  if (!ok) return
  loading.value = true
  try {
    await Promise.all(selectedRows.value.map((r) => basicUserApi.remove(r.id)))
    ElMessage.success('已批量删除')
    tableRef.value?.clearSelection()
    selectedRows.value = []
    loadData()
  } finally {
    loading.value = false
  }
}

async function changeStatus(row) {
  const disable = row.status !== 0
  const ok = await isConfirmed(
    `此操作将${disable ? '停用' : '启用'}用户「${row.username}」，是否继续？`,
  )
  if (!ok) return
  if (disable) await basicUserApi.disable(row.id)
  else await basicUserApi.enable(row.id)
  ElMessage.success(disable ? '已停用' : '已启用')
  loadData()
}

// ---------- 详情弹窗：基本信息 + 关联分录 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

// 命名空间下拉（详情弹窗「重新绑定关联」按命名空间筛选 / 列表展示用）
const namespaces = ref([])
const namespaceMap = ref({})

function nsLabel(id) {
  if (id == null) return '-'
  const n = namespaceMap.value[id]
  return n ? `${n.name}（${n.code}）` : String(id)
}

async function loadNamespaces() {
  try {
    const body = await basicNamespaceApi.listAll()
    namespaces.value = body?.data || []
    namespaceMap.value = Object.fromEntries(namespaces.value.map((n) => [n.id, n]))
  } catch {
    namespaces.value = []
    namespaceMap.value = {}
  }
}

const detailFields = [
  { label: 'ID', prop: 'id' },
  { label: '登录名', prop: 'username' },
  { label: '昵称', prop: 'nickname' },
  { label: '工号', prop: 'empNo' },
  { label: '手机号', prop: 'phone' },
  { label: '邮箱', prop: 'email' },
  { label: '管理员', prop: 'admin', formatter: (v) => (v === 1 ? '管理员' : '普通') },
  { label: '状态', prop: 'status', formatter: (v) => (v === 0 ? '停用' : '正常') },
  { label: '最近角色', prop: 'latestRoleId' },
  { label: '最近登录', prop: 'lastLoginAt', formatter: fmtDateTime },
  { label: '创建时间', prop: 'createTime', formatter: fmtDateTime },
  { label: '更新时间', prop: 'updateTime', formatter: fmtDateTime },
]

const detailTabs = [
  {
    key: 'roles',
    title: '关联角色',
    keywordLabel: '角色名称',
    namespaceFilter: true,
    boundList: (id) => basicUserApi.roles(id),
    boundSet: (id, ids) => basicUserApi.setRoles(id, ids),
    // 用户不归属命名空间：可跨命名空间 / 跨应用关联角色；这里按命名空间筛选候选
    sourceList: ({ keyword, boundIds, namespaceId, current, pageSize }) =>
      basicRoleApi.list({ current, pageSize, name: keyword, boundIds, namespaceId }),
  },
  {
    key: 'groups',
    title: '关联用户组（岗位）',
    keywordLabel: '岗位名称',
    namespaceFilter: true,
    boundList: (id) => basicUserApi.groups(id),
    boundSet: (id, ids) => basicUserApi.setGroups(id, ids),
    sourceList: ({ keyword, boundIds, namespaceId, current, pageSize }) =>
      basicGroupApi.list({ current, pageSize, name: keyword, boundIds, namespaceId }),
  },
]

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
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
    { required: true, message: '请输入工号', trigger: 'blur' },
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

// ---------- 创建成功后的一次性默认密码（不落库、不缓存） ----------
const createdPwdVisible = ref(false)
const createdPwdForm = reactive({
  username: '',
  password: '',
})

function closeCreatedPwdDialog() {
  createdPwdVisible.value = false
  createdPwdForm.username = ''
  createdPwdForm.password = ''
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
        // 密码由后台下发默认密码，data.password 为明文，仅此次返回
        const body = await basicUserApi.create(payload)
        createdPwdForm.username = body?.data?.username || form.username
        createdPwdForm.password = body?.data?.password || ''
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
      if (!currentRow.value && createdPwdForm.password) {
        createdPwdVisible.value = true
      }
    } finally {
      saving.value = false
    }
  })
}

// ---------- 重置密码（高危，需 system:user:reset-pwd） ----------
const resetVisible = ref(false)
const resetting = ref(false)
const resetRow = ref(null)
const resetForm = reactive({
  password: '',
  unlock: true,
})
// 重置成功后展示的一次性明文密码（不落库、不缓存）
const resetResult = ref('')

function openResetPassword(row) {
  resetRow.value = row
  resetForm.password = ''
  resetForm.unlock = true
  resetResult.value = ''
  resetVisible.value = true
}

async function handleResetPassword() {
  if (!resetRow.value) return
  const pwd = resetForm.password.trim()
  if (pwd && pwd.length < 8) {
    ElMessage.warning('密码长度不能少于 8 位')
    return
  }
  resetting.value = true
  try {
    const body = await basicUserApi.resetPassword(resetRow.value.id, {
      password: pwd || undefined,
      unlock: resetForm.unlock,
    })
    resetResult.value = body?.data?.password || ''
    ElMessage.success('密码已重置')
    loadData()
  } finally {
    resetting.value = false
  }
}

function closeResetDialog() {
  resetVisible.value = false
  resetResult.value = ''
}

onMounted(() => {
  loadData()
  loadNamespaces()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
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
        <el-form-item label="工号">
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
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作区：新增 + 批量删除（位于查询区域下方，对齐 maku-admin 原生布局） -->
      <div class="pro-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
        <el-button
          v-auth="'system:user:delete'"
          type="danger"
          plain
          :icon="Delete"
          :disabled="!selectedRows.length"
          @click="handleBatchRemove"
        >
          批量删除{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
        </el-button>
      </div>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="rows"
          row-key="id"
          stripe
          @selection-change="handleSelectionChange"
          border
        >
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="登录名" min-width="130" align="center">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.username)">{{ row.username || '-' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" min-width="120" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nickname || '-' }}</template>
          </el-table-column>
          <el-table-column label="工号" min-width="120" align="center">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.empNo)">{{ row.empNo || '-' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130" align="center" class-name="col-center">
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column label="用户组" min-width="160" align="center">
            <template #default="{ row }">
              <el-tag
                v-for="g in groupMap[row.id] || []"
                :key="g"
                size="small"
                type="primary"
                effect="light"
                class="group-tag"
              >
                {{ g }}
              </el-tag>
              <span v-if="!(groupMap[row.id] || []).length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'danger' : 'primary'" size="small" effect="light">
                {{ row.status === 0 ? '禁用' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录时间" width="170" align="center" class-name="col-center">
            <template #default="{ row }">{{ fmtDateTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link type="primary" class="link-op" @click="changeStatus(row)">
                {{ row.status === 0 ? '启用' : '停用' }}
              </el-link>
              <el-link
                v-auth="'system:user:reset-pwd'"
                type="primary"
                class="link-op"
                @click="openResetPassword(row)"
              >
                重置密码
              </el-link>
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
        <el-form-item label="工号" prop="empNo">
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
      </el-form>
      <template #footer>
        <div class="pro-drawer-footer">
          <el-button @click="drawerOpen = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 创建成功：展示后台下发的默认密码（明文仅出现这一次） -->
    <el-dialog
      v-model="createdPwdVisible"
      title="创建成功"
      width="480px"
      :close-on-click-modal="false"
      :show-close="false"
      @closed="closeCreatedPwdDialog"
    >
      <el-form label-width="110px">
        <el-form-item label="登录名">
          <span>{{ createdPwdForm.username || '-' }}</span>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-space>
            <el-tag type="success" effect="light">{{ createdPwdForm.password }}</el-tag>
            <el-button size="small" @click="copyText(createdPwdForm.password)">复制</el-button>
          </el-space>
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该初始密码由系统下发，关闭弹窗后将无法再次查看，请及时转告用户并提醒其登录后修改。"
      />
      <template #footer>
        <el-button type="primary" @click="closeCreatedPwdDialog">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog
      v-model="resetVisible"
      title="重置密码"
      width="480px"
      :close-on-click-modal="false"
      @closed="closeResetDialog"
    >
      <el-form label-width="110px">
        <el-form-item label="用户">
          <span>{{ resetRow?.username || '-' }}</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="resetForm.password"
            type="password"
            show-password
            placeholder="留空则使用系统默认密码"
          />
        </el-form-item>
        <el-form-item label="同时启用账号">
          <el-switch v-model="resetForm.unlock" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="重置后会清零该用户的连续登录失败次数；旧 token 在过期前仍可使用。"
          style="margin-bottom: 12px"
        />
        <el-form-item v-if="resetResult" label="新密码明文">
          <el-space>
            <el-tag type="success" effect="light">{{ resetResult }}</el-tag>
            <el-button size="small" @click="copyText(resetResult)">复制</el-button>
          </el-space>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeResetDialog">关闭</el-button>
        <el-button type="primary" :loading="resetting" @click="handleResetPassword">
          确定重置
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗：上基本信息 + 下关联分录页签 -->
    <RelationDetailDialog
      v-model="detailVisible"
      title="用户详情"
      :row="detailRow"
      :fields="detailFields"
      :tabs="detailTabs"
    >
      <template #roles-columns>
        <el-table-column prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="命名空间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ nsLabel(row.namespaceId) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'warning' : 'info'">
              {{ roleTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <template #groups-columns>
        <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="岗位编码" min-width="130" show-overflow-tooltip />
        <el-table-column label="命名空间" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ nsLabel(row.namespaceId) }}</template>
        </el-table-column>
      </template>
    </RelationDetailDialog>
  </div>
</template>
