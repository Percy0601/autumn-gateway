<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { basicGroupApi } from '../../../api/basic'

/**
 * 方向：
 *  - parent2child：左侧选上级组，右侧勾选它的下级组
 *  - child2parent：左侧选下级组，右侧勾选它的上级组
 * 两个方向共用 basic_group_group 一张表（parent_group_id / group_id）。
 */
const direction = ref('parent2child')

const loadingLeft = ref(false)
const loadingRight = ref(false)
const saving = ref(false)
const groupList = ref([])

const selected = ref(null)
const checkedIds = ref([])
const rightTableRef = ref()

const cfg = computed(() =>
  direction.value === 'parent2child'
    ? {
        leftTitle: '上级用户组',
        rightTitle: '下级用户组',
        load: (id) => basicGroupApi.subGroups(id),
        save: (id, ids) => basicGroupApi.setSubGroups(id, ids),
        okMsg: '已保存下级用户组',
      }
    : {
        leftTitle: '下级用户组',
        rightTitle: '上级用户组',
        load: (id) => basicGroupApi.parentGroups(id),
        save: (id, ids) => basicGroupApi.setParentGroups(id, ids),
        okMsg: '已保存上级用户组',
      },
)

/** 右侧候选要排除当前选中的组，避免把自己配成自己的上下级 */
const rightData = computed(() =>
  selected.value ? groupList.value.filter((g) => g.id !== selected.value.id) : groupList.value,
)

async function loadGroups() {
  loadingLeft.value = true
  try {
    const body = await basicGroupApi.list({ current: 1, pageSize: 9999 })
    groupList.value = (body.data || []).filter((g) => !g.deleted)
  } finally {
    loadingLeft.value = false
  }
}

async function selectGroup(row) {
  selected.value = row
  loadingRight.value = true
  try {
    const res = await cfg.value.load(row.id)
    checkedIds.value = (res.data || []).map((g) => g.id ?? g)
    await nextTick()
    rightData.value.forEach((g) => {
      rightTableRef.value?.toggleRowSelection(g, checkedIds.value.includes(g.id))
    })
  } finally {
    loadingRight.value = false
  }
}

function onSelectionChange(rows) {
  checkedIds.value = rows.map((g) => g.id)
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

onMounted(loadGroups)
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">用户组 - 用户组管理（组层级）</div>
        <div class="head-actions">
          <el-radio-group v-model="direction" size="small">
            <el-radio-button value="parent2child">配下级组</el-radio-button>
            <el-radio-button value="child2parent">配上级组</el-radio-button>
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
            :data="groupList"
            row-key="id"
            stripe
            highlight-current-row
            height="520"
            @row-click="selectGroup"
          >
            <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="code" label="岗位编码" min-width="120" show-overflow-tooltip />
          </el-table>
        </div>

        <!-- 右侧：多选 -->
        <div class="assoc-col">
          <div class="assoc-col-title">
            {{ cfg.rightTitle }}（{{
              selected ? `已选：${selected.name}` : `请选择左侧${cfg.leftTitle}`
            }}）
          </div>
          <el-table
            ref="rightTableRef"
            v-loading="loadingRight"
            :data="rightData"
            row-key="id"
            stripe
            height="520"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="45" />
            <el-table-column prop="name" label="岗位名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="code" label="岗位编码" min-width="120" show-overflow-tooltip />
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
