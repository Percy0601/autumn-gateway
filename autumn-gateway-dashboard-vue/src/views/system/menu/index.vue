<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicAppApi, basicMenuApi, basicResourceApi } from '../../../api/basic'
import RelationDetailDialog from '../../../components/RelationDetailDialog.vue'
import {
  appIdLabel,
  fmtDateTime,
  MENU_STATUS_TAG,
  MENU_TYPE_OPTIONS,
  menuTypeLabel,
} from '../../../utils/format'

const loading = ref(false)
const flatRows = ref([])

// 所属应用下拉：从应用列表（basic_app）查询，而非写死的枚举
const appOptions = ref([])

async function loadApps() {
  try {
    const body = await basicAppApi.listAll()
    appOptions.value = body.data || []
  } catch {
    appOptions.value = []
  }
}

/** 表格展示：优先用应用名称，查不到时回退到应用编号枚举 */
const appLabel = (id) => {
  const app = appOptions.value.find((a) => a.id === id)
  return app ? `${app.name}（${app.appCode}）` : appIdLabel(id)
}

const query = reactive({
  appId: undefined,
  keyword: '',
})

// 过滤后的扁平数据
const filteredFlat = computed(() => {
  const kw = query.keyword.trim().toLowerCase()
  return flatRows.value.filter((m) => {
    const matchApp = !query.appId || m.appId === query.appId
    const matchKw =
      !kw ||
      (m.menuName || '').toLowerCase().includes(kw) ||
      (m.menuIdentification || '').toLowerCase().includes(kw)
    return matchApp && matchKw
  })
})

// 构建树
function buildTree(list) {
  const map = new Map()
  const roots = []
  list.forEach((item) => {
    map.set(item.id, { ...item, children: [] })
  })
  map.forEach((item) => {
    if (item.parentId && map.has(item.parentId)) {
      map.get(item.parentId).children.push(item)
    } else {
      roots.push(item)
    }
  })
  const sortNodes = (nodes) =>
    nodes
      .sort((a, b) => (a.sortNum || 0) - (b.sortNum || 0))
      .map((n) => ({
        ...n,
        children: n.children.length ? sortNodes(n.children) : undefined,
      }))
  return sortNodes(roots)
}

const treeData = computed(() => buildTree(filteredFlat.value))

const treeKey = computed(() => `${query.appId ?? ''}-${query.keyword}`)

async function loadData() {
  loading.value = true
  try {
    const body = await basicMenuApi.list({
      appId: query.appId,
      current: 1,
      pageSize: 9999,
    })
    flatRows.value = body.data || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

// ---------- 详情弹窗：基本信息 + 关联分录 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

const detailFields = [
  { label: 'ID', prop: 'id' },
  { label: '菜单名称', prop: 'menuName' },
  { label: '菜单类型', prop: 'menuType', formatter: menuTypeLabel },
  { label: '所属应用', prop: 'appId', formatter: appLabel },
  { label: '上级菜单', prop: 'parentId', formatter: (v) => (v ? `#${v}` : '顶级菜单') },
  { label: '菜单URL', prop: 'menuUrl' },
  { label: '菜单标识', prop: 'menuIdentification' },
  { label: '菜单图标', prop: 'menuIcon' },
  { label: '排序', prop: 'sortNum' },
  { label: '状态', prop: 'menuStatus', formatter: (v) => (v === 1 ? '启用' : '停用') },
  { label: '备注', prop: 'remark' },
  { label: '创建时间', prop: 'createTime', formatter: fmtDateTime },
]

const detailTabs = [
  {
    key: 'roles',
    title: '关联角色',
    readonly: true, // 反查：哪些角色授权了这个菜单，只读展示
    boundList: (id) => basicMenuApi.roles(id),
  },
  {
    key: 'resources',
    title: '关联资源',
    keywordLabel: '资源名称',
    boundList: (id) => basicMenuApi.resources(id),
    boundSet: (id, ids) => basicMenuApi.setResources(id, ids),
    sourceList: ({ keyword, boundIds, current, pageSize }) =>
      basicResourceApi.list({ current, pageSize, name: keyword, boundIds }),
  },
]

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

// ---------- 表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()

const form = reactive({
  appId: undefined,
  parentId: 0,
  menuType: 2,
  menuName: '',
  menuUrl: '',
  menuIdentification: '',
  menuIcon: '',
  sortNum: 0,
  menuStatus: true,
  remark: '',
})

const rules = {
  appId: [{ required: true, message: '请选择所属应用', trigger: 'change' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
}

// 父级菜单下拉（仅展示同应用、且非自身及其子级）
const parentOptions = computed(() => {
  const list = flatRows.value
    .filter((m) => m.appId === form.appId && (!currentRow.value || m.id !== currentRow.value.id))
    .map((m) => ({ id: m.id, menuName: m.menuName, parentId: m.parentId }))
  const map = new Map(list.map((m) => [m.id, { ...m, children: [] }]))
  const roots = []
  map.forEach((m) => {
    if (m.parentId && map.has(m.parentId)) map.get(m.parentId).children.push(m)
    else roots.push(m)
  })
  const toTree = (nodes) =>
    nodes.map((n) => ({
      label: n.menuName,
      value: n.id,
      children: n.children.length ? toTree(n.children) : undefined,
    }))
  return [{ label: '顶级菜单', value: 0, children: toTree(roots) }]
})

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    appId: query.appId,
    parentId: 0,
    menuType: 2,
    menuName: '',
    menuUrl: '',
    menuIdentification: '',
    menuIcon: '',
    sortNum: 0,
    menuStatus: true,
    remark: '',
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    appId: row.appId,
    parentId: row.parentId || 0,
    menuType: row.menuType,
    menuName: row.menuName,
    menuUrl: row.menuUrl || '',
    menuIdentification: row.menuIdentification || '',
    menuIcon: row.menuIcon || '',
    sortNum: row.sortNum || 0,
    menuStatus: row.menuStatus === 1,
    remark: row.remark || '',
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
        parentId: Number(form.parentId) || 0,
        menuType: Number(form.menuType),
        menuName: form.menuName,
        menuUrl: form.menuUrl || undefined,
        menuIdentification: form.menuIdentification || undefined,
        menuIcon: form.menuIcon || undefined,
        sortNum: Number(form.sortNum) || 0,
        menuStatus: form.menuStatus ? 1 : 2,
        remark: form.remark || undefined,
      }
      if (currentRow.value) {
        await basicMenuApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicMenuApi.create(payload)
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

async function changeStatus(row) {
  const disable = row.menuStatus === 1
  if (disable) await basicMenuApi.disable(row.id)
  else await basicMenuApi.enable(row.id)
  ElMessage.success(disable ? '已停用' : '已启用')
  loadData()
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
        <div class="pro-card-title">
          菜单管理
          <el-tag size="small" type="info">{{ flatRows.length }} 项</el-tag>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增菜单</el-button>
        </div>
      </div>

      <!-- 筛选 -->
      <div class="filter-bar">
        <el-select
          v-model="query.appId"
          placeholder="所属应用"
          clearable
          style="width: 180px"
          @change="loadData"
        >
          <el-option
            v-for="app in appOptions"
            :key="app.id"
            :label="app.name"
            :value="app.id"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索名称/标识"
          clearable
          :prefix-icon="Search"
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </div>

      <!-- 树形表格 -->
      <div class="pro-table-wrap">
        <el-table
          :key="treeKey"
          v-loading="loading"
          :data="treeData"
          row-key="id"
          stripe
          default-expand-all
          :tree-props="{ children: 'children' }"
        >
          <el-table-column label="菜单名称" min-width="200">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.menuType === 1 ? 'warning' : row.menuType === 3 ? 'info' : 'primary'"
                effect="light"
                style="margin-right: 8px"
              >
                {{ menuTypeLabel(row.menuType) }}
              </el-tag>
              <span>{{ row.menuName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="所属应用" min-width="150">
            <template #default="{ row }">{{ appLabel(row.appId) }}</template>
          </el-table-column>
          <el-table-column label="菜单URL" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <code v-if="row.menuUrl" class="path-code">{{ row.menuUrl }}</code>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="标识" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.menuIdentification || '-' }}</template>
          </el-table-column>
          <el-table-column prop="menuIcon" label="图标" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="MENU_STATUS_TAG[row.menuStatus] || 'info'" size="small">
                {{ row.menuStatus === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortNum" label="排序" width="60" align="center" />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-link type="info" class="link-op" @click="openDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-popconfirm
                :title="row.menuStatus === 1 ? '确定停用该菜单？' : '确定启用该菜单？'"
                width="160"
                @confirm="changeStatus(row)"
              >
                <template #reference>
                  <el-link :class="row.menuStatus === 1 ? 'link-op disabled-link' : 'link-op enabled-link'">
                    {{ row.menuStatus === 1 ? '停用' : '启用' }}
                  </el-link>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="drawerOpen"
      :title="currentRow ? '编辑菜单' : '新增菜单'"
      size="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属应用" prop="appId">
          <el-select v-model="form.appId" placeholder="请选择应用" filterable style="width: 100%">
            <el-option
              v-for="app in appOptions"
              :key="app.id"
              :label="`${app.name}（${app.appCode}）`"
              :value="app.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option
              v-for="opt in MENU_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'label', children: 'children' }"
            node-key="value"
            check-strictly
            clearable
            filterable
            default-expand-all
            placeholder="选择上级（空=顶级）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="例如：订单列表" />
        </el-form-item>
        <el-form-item label="菜单URL">
          <el-input v-model="form.menuUrl" placeholder="例如：/order/list" />
        </el-form-item>
        <el-form-item label="菜单标识">
          <el-input v-model="form.menuIdentification" placeholder="例如：order:list" />
        </el-form-item>
        <el-form-item label="菜单图标">
          <el-input v-model="form.menuIcon" placeholder="例如：Document" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNum" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.menuStatus" active-text="启用" inactive-text="停用" />
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
      title="菜单详情"
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
      <template #resources-columns>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="资源名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="path" label="资源路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="httpMethod" label="方法" width="90" />
      </template>
    </RelationDetailDialog>
  </div>
</template>

<style scoped>
.toolbar-actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.path-code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
