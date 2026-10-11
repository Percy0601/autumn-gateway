<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basicGroupApi, basicUserApi } from '../../../api/basic'

const loadingUsers = ref(false)
const loadingGroups = ref(false)
const saving = ref(false)
const userList = ref([])
const groupList = ref([])

const selectedUser = ref(null)
const selectedGroupIds = ref([])
const rightTableRef = ref()

async function loadUsers() {
  loadingUsers.value = true
  try {
    const body = await basicUserApi.list({ current: 1, pageSize: 9999 })
    userList.value = body.data || []
  } finally {
    loadingUsers.value = false
  }
}

async function loadGroups() {
  loadingGroups.value = true
  try {
    const body = await basicGroupApi.listAll()
    groupList.value = body.data || []
  } finally {
    loadingGroups.value = false
  }
}

async function selectUser(row) {
  selectedUser.value = row
  loadingGroups.value = true
  try {
    const res = await basicUserApi.groups(row.id)
    selectedGroupIds.value = (res.data || []).map((g) => g.id ?? g)
    await nextTick()
    groupList.value.forEach((g) => {
      rightTableRef.value?.toggleRowSelection(g, selectedGroupIds.value.includes(g.id))
    })
  } finally {
    loadingGroups.value = false
  }
}

function onSelectionChange(rows) {
  selectedGroupIds.value = rows.map((g) => g.id)
}

async function save() {
  if (!selectedUser.value) {
    ElMessage.warning('请先选择左侧用户')
    return
  }
  saving.value = true
  try {
    await basicUserApi.setGroups(selectedUser.value.id, selectedGroupIds.value)
    ElMessage.success('已保存用户-用户组关联')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadGroups()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">用户 - 用户组管理</div>
        <div>
          <el-button
            type="primary"
            :disabled="!selectedUser"
            :loading="saving"
            @click="save"
          >
            保存关联
          </el-button>
        </div>
      </div>

      <div class="assoc-wrap">
        <!-- 左侧：用户 -->
        <div class="assoc-col">
          <div class="assoc-col-title">用户</div>
          <el-table
            v-loading="loadingUsers"
            :data="userList"
            row-key="id"
            stripe
            highlight-current-row
            height="520"
            @row-click="selectUser"
          >
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="empNo" label="员工工号" min-width="140" show-overflow-tooltip />
            <el-table-column label="管理员" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.admin === 1 ? 'danger' : 'info'" size="small">
                  {{ row.admin === 1 ? '管理员' : '普通' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 右侧：用户组 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            用户组（{{ selectedUser ? `已选用户：${selectedUser.empNo}` : '请选择左侧用户' }}）
          </div>
          <el-table
            ref="rightTableRef"
            v-loading="loadingGroups"
            :data="groupList"
            row-key="id"
            stripe
            height="520"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="岗位名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="code" label="岗位编码" min-width="120" show-overflow-tooltip />
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
