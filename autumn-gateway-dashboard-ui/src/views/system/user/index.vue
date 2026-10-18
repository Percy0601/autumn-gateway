<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicGroupApi, basicRoleApi, basicUserApi } from '../../../api/basic'
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
    boundList: (id) => basicUserApi.roles(id),
    boundSet: (id, ids) => basicUserApi.setRoles(id, ids),
    // 用户不归属命名空间：可跨命名空间 / 跨应用关联角色（未来做独立授权）
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicRoleApi.list({ current, pageSize, name: keyword, boundIds }),
  },
  {
    key: 'groups',
    title: '关联用户组（岗位）',
    keywordLabel: '岗位名称',
    boundList: (id) => basicUserApi.groups(id),
    boundSet: (id, ids) => basicUserApi.setGroups(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicGroupApi.list({ current, pageSize, name: keyword, boundIds }),
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
      </template>
    </RelationDetailDialog>
  </div>
</template>
