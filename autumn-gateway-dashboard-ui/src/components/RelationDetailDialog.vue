<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Link, Search } from '@element-plus/icons-vue'

/**
 * 通用「实体详情 + 关联分录」弹窗（ERP 单据头 + 分录 风格）。
 *
 * 上半部分：实体自身字段（el-descriptions，只读）
 * 下半部分：多个页签，每个页签是一张只读分录表格，默认每页 50 条（可切换）
 *
 * 用法：
 *   <RelationDetailDialog v-model="visible" :row="row" :fields="fields" :tabs="tabs">
 *     <template #roles-columns> <el-table-column .../> </template>
 *   </RelationDetailDialog>
 *
 * fields: [{ label, prop, formatter? }]
 * tabs:   [{ key, title, keywordLabel, readonly, boundList(id), boundSet(id, ids), sourceList(params) }]
 *         - readonly   : true 表示纯展示（如反查页签），隐藏「重新绑定关联」
 *         - boundList  : 查询已关联列表（返回 Result body）
 *         - boundSet   : 全量覆盖关联
 *         - sourceList : 候选查询，params = { keyword, boundIds, current, pageSize }
 *                        boundIds 传给后端，保证已关联的记录一定出现且排在最前
 * 列渲染通过具名插槽 `${key}-columns` 提供，分录表格与选择弹窗共用同一组列。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '详情' },
  row: { type: Object, default: null },
  fields: { type: Array, default: () => [] },
  tabs: { type: Array, default: () => [] },
  width: { type: String, default: '900px' },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const activeTab = ref('')
const rowsMap = ref({}) // { tabKey: 已关联行 }
const loadingMap = ref({})
/** 每个分录页签的本地分页状态，默认每页 50 条 */
const tabPage = ref({})

/** 已关联 id 缓存：既用于弹窗回显勾选，也传给后端做置顶 */
const boundIds = (key) => (rowsMap.value[key] || []).map((r) => r.id)

function pagedRows(key) {
  const rows = rowsMap.value[key] || []
  const p = tabPage.value[key] || { current: 1, pageSize: 50 }
  const start = (p.current - 1) * p.pageSize
  return rows.slice(start, start + p.pageSize)
}

const pick = ref({
  open: false,
  tab: null,
  keyword: '',
  rows: [],
  total: 0,
  current: 1,
  pageSize: 20,
  loading: false,
  saving: false,
  selection: [],
})
const pickTableRef = ref()

watch(visible, (v) => {
  if (!v) {
    rowsMap.value = {}
    return
  }
  activeTab.value = props.tabs.length ? props.tabs[0].key : ''
  // 先建好分页状态，避免表格先渲染时取到 undefined
  props.tabs.forEach((t) => {
    tabPage.value[t.key] = { current: 1, pageSize: 50 }
  })
  props.tabs.forEach(loadBound)
})

function display(row, field) {
  const v = row?.[field.prop]
  if (v === null || v === undefined || v === '') return '-'
  return field.formatter ? field.formatter(v, row) : v
}

async function loadBound(tab) {
  if (!props.row?.id) return
  loadingMap.value[tab.key] = true
  tabPage.value[tab.key] = { current: 1, pageSize: 50 }
  try {
    const body = await tab.boundList(props.row.id)
    rowsMap.value[tab.key] = body?.data || []
  } finally {
    loadingMap.value[tab.key] = false
  }
}

// ---------- 重新绑定关联 ----------

function openPicker(tab) {
  pick.value.tab = tab
  pick.value.keyword = ''
  pick.value.current = 1
  pick.value.selection = []
  pick.value.rows = []
  pick.value.open = true
  loadCandidates()
}

async function loadCandidates() {
  const tab = pick.value.tab
  if (!tab) return
  pick.value.loading = true
  try {
    const body = await tab.sourceList({
      keyword: pick.value.keyword,
      boundIds: boundIds(tab.key).join(','),
      current: pick.value.current,
      pageSize: pick.value.pageSize,
    })
    pick.value.rows = body?.data || []
    pick.value.total = body?.total || 0
    // 已关联的行默认勾选（后端已把它们排在最前）
    const bound = boundIds(tab.key)
    await nextTick()
    pick.value.rows.forEach((r) => {
      pickTableRef.value?.toggleRowSelection(r, bound.includes(r.id))
    })
  } finally {
    pick.value.loading = false
  }
}

function pickSearch() {
  pick.value.current = 1
  loadCandidates()
}

function pickReset() {
  pick.value.keyword = ''
  pickSearch()
}

function onPickSelectionChange(rows) {
  pick.value.selection = rows
}

function isPicked(row) {
  return pick.value.selection.some((s) => s.id === row.id)
}

async function confirmPick() {
  const tab = pick.value.tab
  if (!tab) return
  pick.value.saving = true
  try {
    await tab.boundSet(props.row.id, pick.value.selection.map((r) => r.id))
    ElMessage.success('已保存关联')
    pick.value.open = false
    await loadBound(tab)
    emit('saved')
  } finally {
    pick.value.saving = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" :width="width" top="5vh">
    <!-- 上半部分：实体自身信息 -->
    <el-descriptions :column="3" border size="small" class="detail-desc">
      <el-descriptions-item v-for="f in fields" :key="f.prop" :label="f.label">
        <slot :name="`field-${f.prop}`" :row="row">{{ display(row, f) }}</slot>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 下半部分：关联分录页签（只读 + 分页） -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key">
        <template #label>
          {{ tab.title }}（{{ (rowsMap[tab.key] || []).length }}）
        </template>
        <div class="tab-toolbar">
          <el-button
            v-if="!tab.readonly"
            type="primary"
            size="small"
            :icon="Link"
            @click="openPicker(tab)"
          >
            重新绑定关联
          </el-button>
        </div>
        <el-table
          v-loading="loadingMap[tab.key]"
          :data="pagedRows(tab.key)"
          row-key="id"
          stripe
          size="small"
          max-height="300"
          border
        >
          <slot :name="`${tab.key}-columns`" />
          <template #empty>
            <span class="empty-tip">暂无关联数据</span>
          </template>
        </el-table>
        <div v-if="(rowsMap[tab.key] || []).length" class="tab-pagination">
          <el-pagination
            v-model:current-page="tabPage[tab.key].current"
            v-model:page-size="tabPage[tab.key].pageSize"
            :total="(rowsMap[tab.key] || []).length"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 重新绑定关联：选择弹窗 -->
    <el-dialog
      v-model="pick.open"
      :title="`选择${pick.tab?.title || ''}`"
      width="780px"
      append-to-body
    >
      <el-form inline class="pick-search" @submit.prevent>
        <el-form-item :label="pick.tab?.keywordLabel || '关键字'">
          <el-input
            v-model="pick.keyword"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="pickSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :icon="Search" @click="pickSearch">
            查询
          </el-button>
          <el-button size="small" @click="pickReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="pickTableRef"
        v-loading="pick.loading"
        :data="pick.rows"
        row-key="id"
        stripe
        size="small"
        max-height="340"
        @selection-change="onPickSelectionChange"
        border
      >
        <el-table-column type="selection" width="45" reserve-selection />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="isPicked(row)" size="small" type="success">已关联</el-tag>
          </template>
        </el-table-column>
        <slot :name="`${pick.tab?.key}-columns`" />
      </el-table>

      <div class="pick-pagination">
        <el-pagination
          v-model:current-page="pick.current"
          v-model:page-size="pick.pageSize"
          :total="pick.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadCandidates"
          @size-change="pickSearch"
        />
      </div>

      <template #footer>
        <span class="pick-count">已选 {{ pick.selection.length }} 项</span>
        <el-button @click="pick.open = false">取消</el-button>
        <el-button type="primary" :loading="pick.saving" @click="confirmPick">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.detail-desc {
  margin-bottom: 4px;
}
.detail-tabs {
  margin-top: 12px;
}
.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  min-height: 24px;
  margin-bottom: 8px;
}
.empty-tip {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}
.tab-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.pick-search {
  margin-bottom: 4px;
}
.pick-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.pick-count {
  margin-right: 12px;
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
}
</style>
