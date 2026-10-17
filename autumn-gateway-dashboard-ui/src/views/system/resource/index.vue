<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicAppApi, basicMenuApi, basicResourceApi } from '../../../api/basic'
import { copyText, fmtDateTime, HTTP_METHODS, MATCH_TYPES } from '../../../utils/format'
import RelationDetailDialog from '../../../components/RelationDetailDialog.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const appOptions = ref([])
const appMap = ref({})

async function loadApps() {
  try {
    const body = await basicAppApi.listAll()
    appOptions.value = (body.data || []).map((a) => ({
      label: `${a.name} (${a.appCode})`,
      value: a.id,
    }))
    const map = {}
    ;(body.data || []).forEach((a) => (map[a.id] = `${a.name} (${a.appCode})`))
    appMap.value = map
  } catch {
    appOptions.value = []
  }
}

const query = reactive({
  current: 1,
  pageSize: 10,
  appId: undefined,
  name: '',
  httpMethod: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicResourceApi.list(query)
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
  query.name = ''
  query.httpMethod = ''
  handleSearch()
}

async function changeStatus(row) {
  const disable = row.status === 1
  if (disable) await basicResourceApi.disable(row.id)
  else await basicResourceApi.enable(row.id)
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

// ---------- 详情弹窗：基本信息 + 关联分录 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

const detailFields = [
  { label: 'ID', prop: 'id' },
  { label: '资源名称', prop: 'name' },
  { label: '所属应用', prop: 'appId', formatter: (v) => appMap.value[v] || `应用#${v}` },
  { label: 'URL 模式', prop: 'path' },
  { label: 'HTTP方法', prop: 'httpMethod' },
  { label: '匹配方式', prop: 'matchType' },
  { label: '状态', prop: 'status', formatter: (v) => (v === 1 ? '正常' : '禁用') },
  { label: '描述', prop: 'description' },
  { label: '创建时间', prop: 'createdAt', formatter: fmtDateTime },
]

const detailTabs = [
  {
    key: 'roles',
    title: '关联角色',
    readonly: true, // 反查：哪些角色授权了这个资源，只读展示
    boundList: (id) => basicResourceApi.roles(id),
  },
  {
    key: 'menus',
    title: '关联菜单',
    keywordLabel: '菜单名称',
    boundList: (id) => basicResourceApi.menus(id),
    boundSet: (id, ids) => basicResourceApi.setMenus(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicMenuApi.list({ current, pageSize, menuName: keyword, boundIds }),
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
  appId: undefined,
  name: '',
  path: '',
  httpMethod: 'ALL',
  matchType: 'exact',
  description: '',
  status: true,
})

const rules = {
  appId: [{ required: true, message: '请选择所属应用', trigger: 'change' }],
  name: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入 URL 模式', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    appId: undefined,
    name: '',
    path: '',
    httpMethod: 'ALL',
    matchType: 'exact',
    description: '',
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    appId: row.appId,
    name: row.name,
    path: row.path || '',
    httpMethod: row.httpMethod || 'ALL',
    matchType: row.matchType || 'exact',
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
        appId: Number(form.appId),
        name: form.name,
        path: form.path,
        httpMethod: form.httpMethod,
        matchType: form.matchType,
        description: form.description || undefined,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await basicResourceApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicResourceApi.create(payload)
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
      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="所属应用">
          <el-select
            v-model="query.appId"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="opt in appOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资源名称">
          <el-input
            v-model="query.name"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="HTTP方法">
          <el-select
            v-model="query.httpMethod"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
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
          <el-table-column label="所属应用" min-width="170" align="center" show-overflow-tooltip>
            <template #default="{ row }">{{ appMap[row.appId] || `应用#${row.appId}` }}</template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="资源名称"
            min-width="140"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column label="URL 模式" min-width="220" align="center">
            <template #default="{ row }">
              <code v-if="row.path" class="path-code">{{ row.path }}</code>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="HTTP" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.httpMethod && row.httpMethod !== 'ALL'" size="small" type="success">
                {{ row.httpMethod }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="matchType" label="匹配" width="90" align="center">
            <template #default="{ row }">{{ row.matchType || '-' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'primary' : 'danger'" size="small" effect="light">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-popconfirm
                :title="row.status === 1 ? '确定禁用？' : '确定启用？'"
                width="160"
                @confirm="changeStatus(row)"
              >
                <template #reference>
                  <el-link type="primary" class="link-op">
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
      :title="currentRow ? '编辑资源' : '新增资源'"
      size="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属应用" prop="appId">
          <el-select v-model="form.appId" placeholder="请选择应用" filterable style="width: 100%">
            <el-option
              v-for="opt in appOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资源名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：创建订单" />
        </el-form-item>
        <el-form-item label="URL 模式" prop="path">
          <el-input v-model="form.path" placeholder="例如：/api/order/:id" />
        </el-form-item>
        <el-form-item label="HTTP方法">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配方式">
          <el-select v-model="form.matchType" style="width: 100%">
            <el-option v-for="m in MATCH_TYPES" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
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

    <!-- 详情弹窗：上基本信息 + 下关联分录页签 -->
    <RelationDetailDialog
      v-model="detailVisible"
      title="资源详情"
      :row="detailRow"
      :fields="detailFields"
      :tabs="detailTabs"
    >
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
    </RelationDetailDialog>
  </div>
</template>

<style scoped>
.path-code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
