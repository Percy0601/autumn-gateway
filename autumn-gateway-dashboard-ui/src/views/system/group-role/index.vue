<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basicGroupApi, basicRoleApi } from '../../../api/basic'
import { appIdLabel, masterLabel, roleTypeLabel } from '../../../utils/format'

const loadingGroups = ref(false)
const loadingRoles = ref(false)
const saving = ref(false)
const groupList = ref([])
const roleList = ref([])

const selectedGroup = ref(null)
const selectedRoleIds = ref([])
const rightTableRef = ref()

async function loadGroups() {
  loadingGroups.value = true
  try {
    const body = await basicGroupApi.list({ current: 1, pageSize: 9999 })
    groupList.value = body.data || []
  } finally {
    loadingGroups.value = false
  }
}

async function loadRoles() {
  loadingRoles.value = true
  try {
    const body = await basicRoleApi.listAll()
    roleList.value = body.data || []
  } finally {
    loadingRoles.value = false
  }
}

async function selectGroup(row) {
  selectedGroup.value = row
  loadingRoles.value = true
  try {
    const res = await basicGroupApi.roles(row.id)
    selectedRoleIds.value = (res.data || []).map((r) => r.id ?? r)
    await nextTick()
    roleList.value.forEach((r) => {
      rightTableRef.value?.toggleRowSelection(r, selectedRoleIds.value.includes(r.id))
    })
  } finally {
    loadingRoles.value = false
  }
}

function onSelectionChange(rows) {
  selectedRoleIds.value = rows.map((r) => r.id)
}

async function save() {
  if (!selectedGroup.value) {
    ElMessage.warning('请先选择左侧用户组')
    return
  }
  saving.value = true
  try {
    await basicGroupApi.setRoles(selectedGroup.value.id, selectedRoleIds.value)
    ElMessage.success('已保存用户组-角色关联')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadGroups()
  loadRoles()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">用户组 - 角色管理</div>
        <div>
          <el-button
            type="primary"
            :disabled="!selectedGroup"
            :loading="saving"
            @click="save"
          >
            保存关联
          </el-button>
        </div>
      </div>

      <div class="assoc-wrap">
        <!-- 左侧：用户组 -->
        <div class="assoc-col">
          <div class="assoc-col-title">用户组</div>
          <el-table
            v-loading="loadingGroups"
            :data="groupList"
            row-key="id"
            stripe
            highlight-current-row
            height="520"
            @row-click="selectGroup"
            border
          >
            <el-table-column prop="name" label="岗位名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="code" label="编码" min-width="100" show-overflow-tooltip />
          </el-table>
        </div>

        <!-- 右侧：角色 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            角色（{{ selectedGroup ? `已选用户组：${selectedGroup.name}` : '请选择左侧用户组' }}）
          </div>
          <el-table
            ref="rightTableRef"
            v-loading="loadingRoles"
            :data="roleList"
            row-key="id"
            stripe
            height="520"
            @selection-change="onSelectionChange"
            border
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="name" label="角色名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="应用" min-width="150">
              <template #default="{ row }">{{ appIdLabel(row.appId) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.type === 1 ? 'warning' : 'info'">
                  {{ roleTypeLabel(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="管理员" width="80" align="center">
              <template #default="{ row }">{{ masterLabel(row.master) }}</template>
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
