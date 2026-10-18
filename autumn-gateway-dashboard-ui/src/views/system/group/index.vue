<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicGroupApi, basicNamespaceApi, basicRoleApi, basicUserApi } from '../../../api/basic'
import { fmtDateTime } from '../../../utils/format'
import { isConfirmed } from '../../../utils/confirm'
import RelationDetailDialog from '../../../components/RelationDetailDialog.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

// 所属命名空间下拉：从命名空间列表（basic_namespace）查询
const namespaceOptions = ref([])

async function loadNamespaces() {
  try {
    const body = await basicNamespaceApi.listAll()
    namespaceOptions.value = body.data || []
  } catch {
    namespaceOptions.value = []
  }
}

/** 表格展示：优先用命名空间名称，查不到时回退到 code / id */
const nsLabel = (row) => {
  const ns = namespaceOptions.value.find((n) => n.id === row.namespaceId)
  if (ns) return `${ns.name}（${ns.code}）`
  return row.namespaceCode || (row.namespaceId != null ? String(row.namespaceId) : '-')
}

const query = reactive({
  current: 1,
  pageSize: 10,
  namespaceId: undefined,
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
  query.namespaceId = undefined
  query.name = ''
  query.code = ''
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
  const names = selectedRows.value.map((r) => r.name || r.code).join('、')
  const ok = await isConfirmed(
    `此操作将删除选中的 ${selectedRows.value.length} 个用户组（${names}），是否继续？`,
  )
  if (!ok) return
  loading.value = true
  try {
    await Promise.all(selectedRows.value.map((r) => basicGroupApi.remove(r.id)))
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
    `此操作将${disable ? '停用' : '启用'}用户组「${row.name}」，是否继续？`,
  )
  if (!ok) return
  if (disable) await basicGroupApi.disable(row.id)
  else await basicGroupApi.enable(row.id)
  ElMessage.success(disable ? '已停用' : '已启用')
  loadData()
}

// ---------- 详情弹窗：基本信息 + 关联分录 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

const detailFields = [
  { label: 'ID', prop: 'id' },
  { label: '命名空间', prop: 'namespaceId', formatter: (v, row) => nsLabel(row) },
  { label: '岗位名称', prop: 'name' },
  { label: '岗位编码', prop: 'code' },
  { label: '部门ID', prop: 'orgId' },
  { label: '岗位ID', prop: 'jobId' },
  { label: '状态', prop: 'status', formatter: (v) => (v === 0 ? '停用' : '正常') },
  { label: '创建时间', prop: 'createTime', formatter: fmtDateTime },
  { label: '更新时间', prop: 'updateTime', formatter: fmtDateTime },
]

const groupSource = ({ keyword, boundIds, current, pageSize }) =>
  basicGroupApi.list({ current, pageSize, name: keyword, boundIds })

const detailTabs = [
  {
    key: 'users',
    title: '关联用户',
    keywordLabel: '登录名',
    boundList: (id) => basicGroupApi.users(id),
    boundSet: (id, ids) => basicGroupApi.setUsers(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicUserApi.list({ current, pageSize, username: keyword, boundIds }),
  },
  {
    key: 'roles',
    title: '关联角色',
    keywordLabel: '角色名称',
    boundList: (id) => basicGroupApi.roles(id),
    boundSet: (id, ids) => basicGroupApi.setRoles(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicRoleApi.list({ current, pageSize, name: keyword, boundIds }),
  },
  {
    key: 'subGroups',
    title: '下级用户组',
    keywordLabel: '岗位名称',
    boundList: (id) => basicGroupApi.subGroups(id),
    boundSet: (id, ids) => basicGroupApi.setSubGroups(id, ids),
    sourceList: groupSource,
  },
  {
    key: 'parentGroups',
    title: '上级用户组',
    keywordLabel: '岗位名称',
    boundList: (id) => basicGroupApi.parentGroups(id),
    boundSet: (id, ids) => basicGroupApi.setParentGroups(id, ids),
    sourceList: groupSource,
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
  namespaceId: undefined,
  name: '',
  code: '',
  orgId: '',
  jobId: '',
  status: true,
})

const rules = {
  namespaceId: [{ required: true, message: '请选择所属命名空间', trigger: 'change' }],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    namespaceId: namespaceOptions.value.length ? namespaceOptions.value[0].id : undefined,
    name: '',
    code: '',
    orgId: '',
    jobId: '',
    status: true,
  })
  // 命名空间列表还没加载出来时先补一次，避免下拉为空
  if (!namespaceOptions.value.length) loadNamespaces()
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    namespaceId: row.namespaceId ?? undefined,
    name: row.name,
    code: row.code,
    orgId: row.orgId ?? '',
    jobId: row.jobId ?? '',
    status: row.status !== 0,
  })
  if (!namespaceOptions.value.length) loadNamespaces()
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        namespaceId: form.namespaceId,
        name: form.name,
        code: form.code,
        orgId: Number(form.orgId) || 0,
        jobId: Number(form.jobId) || 0,
        status: form.status ? 1 : 0,
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

onMounted(() => {
  loadNamespaces()
  loadData()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="所属命名空间">
          <el-select
            v-model="query.namespaceId"
            placeholder="全部"
            clearable
            style="width: 200px"
            @change="handleSearch"
          >
            <el-option
              v-for="ns in namespaceOptions"
              :key="ns.id"
              :label="`${ns.name}（${ns.code}）`"
              :value="ns.id"
            />
          </el-select>
        </el-form-item>
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
          <el-table-column label="所属命名空间" min-width="180" align="center">
            <template #default="{ row }">{{ nsLabel(row) }}</template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="岗位名称"
            min-width="150"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column
            prop="code"
            label="岗位编码"
            min-width="140"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column label="状态" width="80" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'danger' : 'primary'" size="small" effect="light">
                {{ row.status === 0 ? '停用' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170" align="center">
            <template #default="{ row }">{{ fmtDateTime(row.updateTime) }}</template>
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
      :title="currentRow ? '编辑用户组' : '新增用户组'"
      size="440px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="命名空间" prop="namespaceId">
          <el-select v-model="form.namespaceId" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="ns in namespaceOptions"
              :key="ns.id"
              :label="`${ns.name}（${ns.code}）`"
              :value="ns.id"
            />
          </el-select>
        </el-form-item>
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
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="正常" inactive-text="停用" />
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
      title="用户组详情"
      :row="detailRow"
      :fields="detailFields"
      :tabs="detailTabs"
    >
      <template #users-columns>
        <el-table-column prop="username" label="登录名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="empNo" label="员工工号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="130" show-overflow-tooltip class-name="col-center" />
      </template>
      <template #roles-columns>
        <el-table-column prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'warning' : 'info'">
              {{ row.type === 1 ? '系统角色' : '自定义角色' }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <template #subGroups-columns>
        <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="岗位编码" min-width="140" show-overflow-tooltip />
      </template>
      <template #parentGroups-columns>
        <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="岗位编码" min-width="140" show-overflow-tooltip />
      </template>
    </RelationDetailDialog>
  </div>
</template>
