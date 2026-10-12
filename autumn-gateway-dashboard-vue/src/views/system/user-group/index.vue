<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { basicGroupApi, basicUserApi } from '../../../api/basic'

/**
 * 方向：
 *  - user2group：左侧选用户，右侧勾选所属用户组
 *  - group2user：左侧选用户组，右侧勾选组内用户
 * 两个方向共用 basic_user_group 一张表（user_id / group_id）。
 */
const direction = ref('user2group')

const loadingLeft = ref(false)
const loadingRight = ref(false)
const saving = ref(false)
const userList = ref([])
const groupList = ref([])

const selected = ref(null)
const checkedIds = ref([])
const rightTableRef = ref()

const cfg = computed(() =>
  direction.value === 'user2group'
    ? {
        leftTitle: '用户',
        rightTitle: '用户组',
        leftData: userList.value,
        rightData: groupList.value,
        load: (id) => basicUserApi.groups(id),
        save: (id, ids) => basicUserApi.setGroups(id, ids),
        name: (r) => r.nickname || r.username || r.empNo || `用户#${r.id}`,
        okMsg: '已保存用户-用户组关联',
      }
    : {
        leftTitle: '用户组',
        rightTitle: '用户',
        leftData: groupList.value,
        rightData: userList.value,
        load: (id) => basicGroupApi.users(id),
        save: (id, ids) => basicGroupApi.setUsers(id, ids),
        name: (r) => r.name || r.code || `用户组#${r.id}`,
        okMsg: '已保存用户组-用户关联',
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

async function loadGroups() {
  loadingRight.value = true
  try {
    const body = await basicGroupApi.listAll()
    groupList.value = (body.data || []).filter((g) => !g.deleted)
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

watch(direction, () => {
  selected.value = null
  checkedIds.value = []
  nextTick(() => rightTableRef.value?.clearSelection())
})

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
        <div class="head-actions">
          <el-radio-group v-model="direction" size="small">
            <el-radio-button value="user2group">给用户配用户组</el-radio-button>
            <el-radio-button value="group2user">给用户组配用户</el-radio-button>
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
            <template v-if="direction === 'user2group'">
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
              <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="code" label="岗位编码" min-width="120" show-overflow-tooltip />
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
            <template v-if="direction === 'user2group'">
              <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="code" label="岗位编码" min-width="120" show-overflow-tooltip />
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
