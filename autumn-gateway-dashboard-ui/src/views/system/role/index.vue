<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  basicGroupApi,
  basicMenuApi,
  basicNamespaceApi,
  basicResourceApi,
  basicRoleApi,
  basicUserApi,
} from '../../../api/basic'
import RelationDetailDialog from '../../../components/RelationDetailDialog.vue'
import {
  copyText,
  fmtDateTime,
  MASTER_OPTIONS,
  REGEX,
  ROLE_TYPE_OPTIONS,
} from '../../../utils/format'
import { isConfirmed } from '../../../utils/confirm'

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

/** 表格展示：优先用命名空间名称，查不到时回退到 id */
const nsLabel = (id) => {
  const ns = namespaceOptions.value.find((n) => n.id === id)
  return ns ? `${ns.name}（${ns.code}）` : (id != null ? String(id) : '-')
}

const query = reactive({
  current: 1,
  pageSize: 10,
  namespaceId: undefined,
  code: '',
  name: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicRoleApi.list(query)
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
  query.code = ''
  query.name = ''
  handleSearch()
}

async function changeStatus(row) {
  const disable = row.status === 1
  const ok = await isConfirmed(`此操作将${disable ? '停用' : '启用'}角色「${row.name}」，是否继续？`)
  if (!ok) return
  if (disable) await basicRoleApi.disable(row.id)
  else await basicRoleApi.enable(row.id)
  ElMessage.success(disable ? '已停用' : '已启用')
  loadData()
}

// ---------- 详情弹窗：基本信息 + 关联分录 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

const detailFields = [
  { label: 'ID', prop: 'id' },
  { label: '角色编码', prop: 'code' },
  { label: '角色名称', prop: 'name' },
  { label: '命名空间', prop: 'namespaceId', formatter: nsLabel },
  { label: '角色类型', prop: 'type', formatter: (v) => (v === 1 ? '系统角色' : '自定义角色') },
  { label: '是否管理员', prop: 'master', formatter: (v) => (v === 1 ? '管理员' : '普通') },
  { label: '状态', prop: 'status', formatter: (v) => (v === 1 ? '启用' : '停用') },
  { label: '备注', prop: 'remark' },
  { label: '创建时间', prop: 'createTime', formatter: fmtDateTime },
  { label: '更新时间', prop: 'updateTime', formatter: fmtDateTime },
]

const detailTabs = [
  {
    key: 'menus',
    title: '关联菜单',
    keywordLabel: '菜单名称',
    boundList: (id) => basicRoleApi.menus(id),
    boundSet: (id, ids) => basicRoleApi.setMenus(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicMenuApi.list({ current, pageSize, menuName: keyword, boundIds }),
  },
  {
    key: 'resources',
    title: '关联资源',
    keywordLabel: '资源名称',
    boundList: (id) => basicRoleApi.resources(id),
    boundSet: (id, ids) => basicRoleApi.setResources(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicResourceApi.list({ current, pageSize, name: keyword, boundIds }),
  },
  {
    key: 'groups',
    title: '关联用户组',
    keywordLabel: '岗位名称',
    boundList: (id) => basicRoleApi.groups(id),
    boundSet: (id, ids) => basicRoleApi.setGroups(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicGroupApi.list({ current, pageSize, name: keyword, boundIds }),
  },
  {
    key: 'users',
    title: '关联用户',
    keywordLabel: '登录名',
    boundList: (id) => basicRoleApi.users(id),
    boundSet: (id, ids) => basicRoleApi.setUsers(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicUserApi.list({ current, pageSize, username: keyword, boundIds }),
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
  code: '',
  name: '',
  type: 2,
  master: 2,
  remark: '',
  status: true,
})

const rules = {
  namespaceId: [{ required: true, message: '请选择所属命名空间', trigger: 'change' }],
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
    namespaceId: namespaceOptions.value.length ? namespaceOptions.value[0].id : undefined,
    code: '',
    name: '',
    type: 2,
    master: 2,
    remark: '',
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
    code: row.code,
    name: row.name,
    type: row.type || 2,
    master: row.master || 2,
    remark: row.remark || '',
    status: row.status === 1,
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
        ...form,
        remark: form.remark || undefined,
        type: Number(form.type),
        master: Number(form.master),
        status: form.status ? 1 : 2,
      }
      if (currentRow.value) {
        await basicRoleApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicRoleApi.create(payload)
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
          >
            <el-option
              v-for="ns in namespaceOptions"
              :key="ns.id"
              :label="`${ns.name}（${ns.code}）`"
              :value="ns.id"
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
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作区：新增（位于查询区域下方，对齐 maku-admin 原生布局） -->
      <div class="pro-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
      </div>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table v-loading="loading" :data="rows" row-key="id" stripe border>
          <el-table-column label="角色编码" min-width="130" align="center">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="角色名称"
            min-width="140"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column label="所属命名空间" min-width="180" align="center">
            <template #default="{ row }">{{ nsLabel(row.namespaceId) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 1 ? 'warning' : 'info'">
                {{ row.type === 1 ? '系统角色' : '自定义角色' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="管理员" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.master === 1 ? 'danger' : ''">
                {{ row.master === 1 ? '管理员' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'primary' : 'info'" size="small" effect="light">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link type="primary" class="link-op" @click="changeStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
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
      :title="currentRow ? '编辑角色' : '新增角色'"
      size="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="命名空间" prop="namespaceId">
          <el-select
            v-model="form.namespaceId"
            placeholder="请选择命名空间"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="ns in namespaceOptions"
              :key="ns.id"
              :label="`${ns.name}（${ns.code}）`"
              :value="ns.id"
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
        <el-form-item label="角色类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option
              v-for="opt in ROLE_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否管理员" prop="master">
          <el-select v-model="form.master" style="width: 100%">
            <el-option
              v-for="opt in MASTER_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="启用" inactive-text="停用" />
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
      title="角色详情"
      :row="detailRow"
      :fields="detailFields"
      :tabs="detailTabs"
    >
      <template #menus-columns>
        <el-table-column prop="menuName" label="菜单名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="menuUrl" label="菜单地址" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.menuType === 1 ? 'warning' : 'info'">
              {{ row.menuType === 1 ? '目录' : '菜单' }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
      <template #resources-columns>
        <el-table-column prop="name" label="资源名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="path" label="资源路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="httpMethod" label="方法" width="90" />
      </template>
      <template #groups-columns>
        <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="岗位编码" min-width="140" show-overflow-tooltip />
      </template>
      <template #users-columns>
        <el-table-column prop="username" label="登录名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="empNo" label="员工工号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" min-width="130" show-overflow-tooltip class-name="col-center" />
      </template>
    </RelationDetailDialog>
  </div>
</template>
