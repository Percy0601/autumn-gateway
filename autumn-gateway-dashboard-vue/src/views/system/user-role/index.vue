<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { basicRoleApi, basicUserApi } from '../../../api/basic'
import { appIdLabel, masterLabel, roleTypeLabel } from '../../../utils/format'

/**
 * 方向：
 *  - user2role：左侧选用户，右侧勾选要授予的角色
 *  - role2user：左侧选角色，右侧勾选要授予的用户
 * 两个方向共用 basic_user_role 一张表，只是视角不同。
 */
const direction = ref('user2role')

const loadingLeft = ref(false)
const loadingRight = ref(false)
const saving = ref(false)
const userList = ref([])
const roleList = ref([])

const selected = ref(null)
const checkedIds = ref([])
const rightTableRef = ref()

const cfg = computed(() =>
  direction.value === 'user2role'
    ? {
        leftTitle: '用户',
        rightTitle: '角色',
        leftData: userList.value,
        rightData: roleList.value,
        load: (id) => basicUserApi.roles(id),
        save: (id, ids) => basicUserApi.setRoles(id, ids),
        name: (r) => r.nickname || r.username || `用户#${r.id}`,
        okMsg: '已保存用户-角色关联',
      }
    : {
        leftTitle: '角色',
        rightTitle: '用户',
        leftData: roleList.value,
        rightData: userList.value,
        load: (id) => basicRoleApi.users(id),
        save: (id, ids) => basicRoleApi.setUsers(id, ids),
        name: (r) => r.name || r.code || `角色#${r.id}`,
        okMsg: '已保存角色-用户关联',
      },
)

async function loadUsers() {
  loadingLeft.value = true
  try {
    const body = await basicUserApi.list({ current: 1, pageSize: 9999 })
    userList.value = body.data || []
  } finally {
    loadingLeft.value = false
  }
}

async function loadRoles() {
  loadingRight.value = true
  try {
    const body = await basicRoleApi.listAll()
    roleList.value = body.data || []
  } finally {
    loadingRight.value = false
  }
}

async function selectRow(row) {
  selected.value = row
  loadingRight.value = true
  try {
    const res = await cfg.value.load(row.id)
    checkedIds.value = (res.data || []).map((x) => x.id ?? x)
    await nextTick()
    cfg.value.rightData.forEach((r) => {
      rightTableRef.value?.toggleRowSelection(r, checkedIds.value.includes(r.id))
    })
  } finally {
    loadingRight.value = false
  }
}

function onSelectionChange(rows) {
  checkedIds.value = rows.map((r) => r.id)
}

async function save() {
  if (!selected.value) {
    ElMessage.warning(`请先选择左侧${cfg.value.leftTitle}`)
    return
  }
  saving.value = true
  try {
    await cfg.value.save(selected.value.id, checkedIds.value)
    ElMessage.success(cfg.value.okMsg)
  } finally {
    saving.value = false
  }
}

// 切换方向时清空选中态，避免左右数据错位
watch(direction, () => {
  selected.value = null
  checkedIds.value = []
  nextTick(() => rightTableRef.value?.clearSelection())
})

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">用户 - 角色管理</div>
        <div class="head-actions">
          <el-radio-group v-model="direction" size="small">
            <el-radio-button value="user2role">按用户分配角色</el-radio-button>
            <el-radio-button value="role2user">按角色分配用户</el-radio-button>
          </el-radio-group>
          <el-button type="primary" :disabled="!selected" :loading="saving" @click="save">
            保存关联
          </el-button>
        </div>
      </div>

      <div class="assoc-wrap">
        <!-- 左侧：单选 -->
        <div class="assoc-col">
          <div class="assoc-col-title">{{ cfg.leftTitle }}（点击行选择）</div>
          <el-table
            v-loading="loadingLeft"
            :data="cfg.leftData"
            row-key="id"
            stripe
            highlight-current-row
            height="520"
            @row-click="selectRow"
          >
            <template v-if="direction === 'user2role'">
              <el-table-column prop="username" label="登录名" min-width="120" show-overflow-tooltip />
              <el-table-column prop="nickname" label="昵称" min-width="110" show-overflow-tooltip />
              <el-table-column prop="empNo" label="员工工号" min-width="130" show-overflow-tooltip />
              <el-table-column label="管理员" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.admin === 1 ? 'danger' : 'info'" size="small">
                    {{ row.admin === 1 ? '管理员' : '普通' }}
                  </el-tag>
                </template>
              </el-table-column>
            </template>
            <template v-else>
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
            </template>
          </el-table>
        </div>

        <!-- 右侧：多选 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            {{ cfg.rightTitle }}（{{
              selected ? `已选${cfg.leftTitle}：${cfg.name(selected)}` : `请选择左侧${cfg.leftTitle}`
            }}）
          </div>
          <el-table
            ref="rightTableRef"
            v-loading="loadingRight"
            :data="cfg.rightData"
            row-key="id"
            stripe
            height="520"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="45" />
            <template v-if="direction === 'user2role'">
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
            </template>
            <template v-else>
              <el-table-column prop="username" label="登录名" min-width="120" show-overflow-tooltip />
              <el-table-column prop="nickname" label="昵称" min-width="110" show-overflow-tooltip />
              <el-table-column prop="empNo" label="员工工号" min-width="130" show-overflow-tooltip />
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                    {{ row.status === 1 ? '正常' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
            </template>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
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
</style>
