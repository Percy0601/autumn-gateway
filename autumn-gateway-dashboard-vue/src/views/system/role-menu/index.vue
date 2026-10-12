<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basicMenuApi, basicRoleApi } from '../../../api/basic'
import { appIdLabel, menuStatusLabel, menuTypeLabel } from '../../../utils/format'

const loadingRoles = ref(false)
const loadingMenus = ref(false)
const saving = ref(false)
const roleList = ref([])
const menuTree = ref([])
const selectedRole = ref(null)
const treeRef = ref()
const onlyApp = ref(false)

/** 扁平菜单列表按 parentId 组装成树 */
function buildTree(list) {
  const map = new Map()
  list.forEach((m) => map.set(m.id, { ...m, children: [] }))
  const roots = []
  list.forEach((m) => {
    const node = map.get(m.id)
    const parent = Number(m.parentId || 0) ? map.get(Number(m.parentId)) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

/** 按应用过滤树，父节点只要还有子节点就保留 */
function filterByApp(nodes, appId) {
  return nodes
    .map((n) => ({ ...n, children: filterByApp(n.children || [], appId) }))
    .filter((n) => Number(n.appId) === Number(appId) || (n.children && n.children.length))
}

const visibleTree = computed(() => {
  if (!onlyApp.value || !selectedRole.value?.appId) return menuTree.value
  return filterByApp(menuTree.value, selectedRole.value.appId)
})

async function loadRoles() {
  loadingRoles.value = true
  try {
    const body = await basicRoleApi.listAll()
    roleList.value = body.data || []
  } finally {
    loadingRoles.value = false
  }
}

async function loadMenus() {
  loadingMenus.value = true
  try {
    const body = await basicMenuApi.listAll()
    menuTree.value = buildTree(body.data || [])
  } finally {
    loadingMenus.value = false
  }
}

async function selectRole(row) {
  selectedRole.value = row
  loadingMenus.value = true
  try {
    const res = await basicRoleApi.menus(row.id)
    const ids = (res.data || []).map((m) => m.id ?? m)
    await nextTick()
    // 只回显完全勾选的节点，半选状态由 el-tree 依据子节点自动推导
    treeRef.value?.setCheckedKeys(ids, false)
  } finally {
    loadingMenus.value = false
  }
}

function checkAll() {
  const ids = []
  const walk = (nodes) => nodes.forEach((n) => { ids.push(n.id); walk(n.children || []) })
  walk(visibleTree.value)
  treeRef.value?.setCheckedKeys(ids, false)
}

function clearAll() {
  treeRef.value?.setCheckedKeys([], false)
}

const expanded = ref(true)
function toggleExpand() {
  expanded.value = !expanded.value
  const nodesMap = treeRef.value?.store?.nodesMap || {}
  Object.keys(nodesMap).forEach((key) => {
    nodesMap[key].expanded = expanded.value
  })
}

async function save() {
  if (!selectedRole.value) {
    ElMessage.warning('请先选择左侧角色')
    return
  }
  saving.value = true
  try {
    const ids = treeRef.value?.getCheckedKeys() || []
    await basicRoleApi.setMenus(selectedRole.value.id, ids)
    ElMessage.success(`已保存菜单授权（${ids.length} 项）`)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRoles()
  loadMenus()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">角色 - 菜单授权</div>
        <div class="head-actions">
          <el-button size="small" :disabled="!selectedRole" @click="checkAll">全选</el-button>
          <el-button size="small" :disabled="!selectedRole" @click="clearAll">清空</el-button>
          <el-button size="small" @click="toggleExpand">
            {{ expanded ? '折叠' : '展开' }}
          </el-button>
          <el-button type="primary" :disabled="!selectedRole" :loading="saving" @click="save">
            保存授权
          </el-button>
        </div>
      </div>

      <div class="assoc-wrap">
        <!-- 左侧：角色 -->
        <div class="assoc-col">
          <div class="assoc-col-title">角色（点击行选择）</div>
          <el-table
            v-loading="loadingRoles"
            :data="roleList"
            row-key="id"
            stripe
            highlight-current-row
            height="520"
            @row-click="selectRole"
          >
            <el-table-column prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="name" label="角色名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="应用" min-width="150">
              <template #default="{ row }">{{ appIdLabel(row.appId) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右侧：菜单树 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            菜单（{{
              selectedRole ? `已选角色：${selectedRole.name || selectedRole.code}` : '请选择左侧角色'
            }}）
          </div>
          <div class="tree-toolbar">
            <el-checkbox v-model="onlyApp" :disabled="!selectedRole">
              只看该角色所属应用的菜单
            </el-checkbox>
          </div>
          <div v-loading="loadingMenus" class="tree-box">
            <el-tree
              ref="treeRef"
              :data="visibleTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'menuName', children: 'children' }"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span>{{ data.menuName }}</span>
                  <el-tag size="small" type="info">{{ menuTypeLabel(data.menuType) }}</el-tag>
                  <el-tag size="small" effect="plain">{{ appIdLabel(data.appId) }}</el-tag>
                  <el-tag
                    v-if="data.menuStatus !== 1"
                    size="small"
                    type="danger"
                  >
                    {{ menuStatusLabel(data.menuStatus) }}
                  </el-tag>
                  <span v-if="data.menuIdentification" class="node-code">
                    {{ data.menuIdentification }}
                  </span>
                </span>
              </template>
            </el-tree>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.assoc-wrap {
  display: flex;
  gap: 16px;
}
.assoc-col {
  flex: 1;
  min-width: 0;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}
.assoc-col-title {
  padding: 10px 14px;
  font-weight: 600;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.tree-toolbar {
  padding: 8px 14px;
  border-bottom: 1px solid #f0f0f0;
}
.tree-box {
  height: 470px;
  overflow: auto;
  padding: 8px 14px;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
}
.node-code {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
</style>
