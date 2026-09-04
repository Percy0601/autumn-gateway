<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import {
  copyText,
  HTTP_METHODS,
  MATCH_TYPES,
  PERM_TYPES,
  PERM_TYPE_TAG,
  REGEX,
} from '../../../utils/format'

const router = useRouter()

const loading = ref(false)
const applications = ref([])
const flatPermissions = ref([])

// 筛选状态
const keyword = ref('')
const filterAppId = ref(undefined)
const filterPermType = ref(undefined)

const appOptions = ref([])

async function loadData() {
  loading.value = true
  try {
    const [appsRes, permsRes] = await Promise.all([
      http.get('/api/system/app/list'),
      http.get('/api/system/permission/list'),
    ])
    applications.value = appsRes.data || []
    flatPermissions.value = permsRes.data || []
    appOptions.value = applications.value.map((app) => ({
      label: `${app.name} (${app.appid})`,
      value: app.id,
    }))
  } finally {
    loading.value = false
  }
}

// 从扁平列表构建树（与 React 版同款逻辑）
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
  const clean = (nodes) =>
    nodes
      .sort((a, b) => (a.sort || 0) - (b.sort || 0))
      .map((node) => ({
        ...node,
        children:
          node.children && node.children.length > 0
            ? clean(node.children)
            : undefined,
      }))
  return clean(roots)
}

// 递归过滤树
function filterTree(nodes, kw, appId, permType) {
  return nodes.reduce((acc, node) => {
    const matchApp = !appId || node.appId === appId
    const matchType = !permType || node.permType === permType
    const matchKeyword =
      !kw ||
      (node.name || '').toLowerCase().includes(kw) ||
      (node.code || '').toLowerCase().includes(kw) ||
      (node.resourcePath || '').toLowerCase().includes(kw)
    const filteredChildren = node.children
      ? filterTree(node.children, kw, appId, permType)
      : []
    if ((matchApp && matchType && matchKeyword) || filteredChildren.length > 0) {
      acc.push({
        ...node,
        children: filteredChildren.length > 0 ? filteredChildren : node.children,
      })
    }
    return acc
  }, [])
}

const displayTree = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const tree = buildTree(flatPermissions.value)
  return filterTree(tree, kw, filterAppId.value, filterPermType.value)
})

// 筛选条件变化时强制重建表格，保证过滤后仍默认全部展开
const treeKey = computed(
  () =>
    `${keyword.value}-${filterAppId.value ?? ''}-${filterPermType.value ?? ''}`,
)

function appLabel(appId) {
  const app = applications.value.find((a) => a.id === appId)
  return app ? `${app.name} (${app.appid})` : '-'
}

// 表单中父级权限树数据（与 React 版同款）
function buildPermTree(excludeId) {
  const buildNodes = (parentId) =>
    flatPermissions.value
      .filter((p) => p.parentId === parentId && p.id !== excludeId)
      .map((p) => ({
        label: `${p.name} (${p.code})`,
        value: p.id,
        children: buildNodes(p.id),
      }))
  return [{ label: '顶级（无父级）', value: 0, children: buildNodes(0) }]
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
  await http.put(
    `/api/system/permission/${row.id}/${disable ? 'disable' : 'enable'}`,
  )
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

function goDetail(row) {
  router.push(`/system/permission/detail/${row.id}`)
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const parentTreeData = ref([])

const form = reactive({
  appId: undefined,
  permType: 'API',
  code: '',
  name: '',
  category: '',
  resourcePath: '',
  httpMethod: 'ALL',
  matchType: 'exact',
  sort: 0,
  parentId: 0,
  hidden: false,
  status: true,
})

const permTypeOptions = PERM_TYPES.map((t) => ({ label: t, value: t }))
const httpMethodOptions = HTTP_METHODS.map((m) => ({ label: m, value: m }))
const matchTypeOptions = MATCH_TYPES.map((m) => ({ label: m, value: m }))

const rules = {
  appId: [{ required: true, message: '请选择所属应用', trigger: 'change' }],
  permType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  code: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    {
      pattern: REGEX.permCode,
      message: '以小写字母开头，3-64位',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    appId: undefined,
    permType: 'API',
    code: '',
    name: '',
    category: '',
    resourcePath: '',
    httpMethod: 'ALL',
    matchType: 'exact',
    sort: 0,
    parentId: 0,
    hidden: false,
    status: true,
  })
  parentTreeData.value = buildPermTree(undefined)
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    appId: row.appId,
    permType: row.permType,
    code: row.code,
    name: row.name,
    category: row.category || '',
    resourcePath: row.resourcePath || '',
    httpMethod: row.httpMethod || 'ALL',
    matchType: row.matchType || 'exact',
    sort: row.sort || 0,
    parentId: row.parentId || 0,
    hidden: row.hidden === 1,
    status: row.status === 1,
  })
  parentTreeData.value = buildPermTree(row.id)
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        ...form,
        category: form.category || undefined,
        resourcePath: form.resourcePath || undefined,
        sort: Number(form.sort) || 0,
        parentId: Number(form.parentId) || 0,
        hidden: form.hidden ? 1 : 0,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await http.put(`/api/system/permission/${currentRow.value.id}`, payload)
        ElMessage.success('更新成功')
      } else {
        await http.post('/api/system/permission', payload)
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
        <div class="pro-card-title">
          权限树
          <el-tag size="small" type="info">{{ flatPermissions.length }} 项</el-tag>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增权限</el-button>
        </div>
      </div>

      <!-- 筛选工具条 -->
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索名称/编码/路径"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
        />
        <el-select
          v-model="filterAppId"
          placeholder="所属应用"
          clearable
          filterable
          style="width: 190px"
        >
          <el-option
            v-for="opt in appOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select
          v-model="filterPermType"
          placeholder="权限类型"
          clearable
          style="width: 130px"
        >
          <el-option v-for="t in PERM_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
      </div>

      <!-- 树形表格 -->
      <div class="pro-table-wrap">
        <el-table
          :key="treeKey"
          v-loading="loading"
          :data="displayTree"
          row-key="id"
          stripe
          default-expand-all
          :tree-props="{ children: 'children' }"
        >
          <el-table-column label="权限名称" min-width="220">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="PERM_TYPE_TAG[row.permType]"
                effect="light"
                style="margin-right: 8px"
              >
                {{ row.permType }}
              </el-tag>
              <span>{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权限编码" min-width="180">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="所属应用" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ appLabel(row.appId) }}</template>
          </el-table-column>
          <el-table-column label="资源路径" min-width="220">
            <template #default="{ row }">
              <code v-if="row.resourcePath" class="path-code">{{ row.resourcePath }}</code>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="HTTP" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                v-if="row.httpMethod && row.httpMethod !== 'ALL'"
                size="small"
                type="success"
              >
                {{ row.httpMethod }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="60" align="center" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="goDetail(row)">详情</el-link>
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-popconfirm
                :title="row.status === 1 ? '确定禁用？' : '确定启用？'"
                width="160"
                @confirm="changeStatus(row)"
              >
                <template #reference>
                  <el-link
                    :class="row.status === 1 ? 'link-op disabled-link' : 'link-op enabled-link'"
                  >
                    {{ row.status === 1 ? '禁用' : '启用' }}
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
      :title="currentRow ? '编辑权限' : '新增权限'"
      size="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="105px">
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
        <el-form-item label="权限类型" prop="permType">
          <el-select v-model="form.permType" style="width: 100%">
            <el-option
              v-for="opt in permTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!currentRow"
            placeholder="例如：order:create"
          />
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：创建订单" />
        </el-form-item>
        <el-form-item label="分类标签">
          <el-input v-model="form.category" placeholder="例如：订单管理" />
        </el-form-item>
        <el-form-item label="资源路径 (URL)">
          <el-input
            v-model="form.resourcePath"
            placeholder="API: /api/order/:id ｜ MENU: /order/list"
          />
        </el-form-item>
        <el-form-item label="HTTP方法">
          <el-select v-model="form.httpMethod" style="width: 100%">
            <el-option
              v-for="opt in httpMethodOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配方式">
          <el-select v-model="form.matchType" style="width: 100%">
            <el-option
              v-for="opt in matchTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="父级权限">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeData"
            :props="{ label: 'label', children: 'children' }"
            node-key="value"
            check-strictly
            clearable
            filterable
            default-expand-all
            placeholder="选择父级（空=顶级）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单隐藏">
          <el-switch v-model="form.hidden" active-text="是" inactive-text="否" />
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
