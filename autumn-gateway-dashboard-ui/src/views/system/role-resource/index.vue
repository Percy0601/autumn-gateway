<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basicResourceApi, basicRoleApi } from '../../../api/basic'
import { appIdLabel } from '../../../utils/format'

const loadingRoles = ref(false)
const loadingResources = ref(false)
const saving = ref(false)
const roleList = ref([])
const resourceList = ref([])

const selectedRole = ref(null)
const checkedIds = ref([])
const rightTableRef = ref()

async function loadRoles() {
  loadingRoles.value = true
  try {
    const body = await basicRoleApi.listAll()
    roleList.value = body.data || []
  } finally {
    loadingRoles.value = false
  }
}

async function loadResources() {
  loadingResources.value = true
  try {
    const body = await basicResourceApi.listAll()
    resourceList.value = body.data || []
  } finally {
    loadingResources.value = false
  }
}

async function selectRole(row) {
  selectedRole.value = row
  loadingResources.value = true
  try {
    const res = await basicRoleApi.resources(row.id)
    checkedIds.value = (res.data || []).map((r) => r.id ?? r)
    await nextTick()
    resourceList.value.forEach((r) => {
      rightTableRef.value?.toggleRowSelection(r, checkedIds.value.includes(r.id))
    })
  } finally {
    loadingResources.value = false
  }
}

function onSelectionChange(rows) {
  checkedIds.value = rows.map((r) => r.id)
}

async function save() {
  if (!selectedRole.value) {
    ElMessage.warning('请先选择左侧角色')
    return
  }
  saving.value = true
  try {
    await basicRoleApi.setResources(selectedRole.value.id, checkedIds.value)
    ElMessage.success(`已保存资源授权（${checkedIds.value.length} 项）`)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRoles()
  loadResources()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">角色 - 资源授权</div>
        <el-button type="primary" :disabled="!selectedRole" :loading="saving" @click="save">
          保存授权
        </el-button>
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
            border
          >
            <el-table-column prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="name" label="角色名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="应用" min-width="150">
              <template #default="{ row }">{{ appIdLabel(row.appId) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右侧：资源 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            资源（{{
              selectedRole ? `已选角色：${selectedRole.name || selectedRole.code}` : '请选择左侧角色'
            }}）
          </div>
          <el-table
            ref="rightTableRef"
            v-loading="loadingResources"
            :data="resourceList"
            row-key="id"
            stripe
            height="520"
            @selection-change="onSelectionChange"
            border
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="资源名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
            <el-table-column prop="httpMethod" label="方法" width="90" />
            <el-table-column prop="matchType" label="匹配" width="90" />
            <el-table-column label="应用" min-width="150">
              <template #default="{ row }">{{ appIdLabel(row.appId) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
</style>
