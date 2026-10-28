<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayGlobalFilterApi, gatewayTemplateV2Api } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import ValueTreeForm from '../components/ValueTreeForm.vue'
import { defaultOf } from '../components/slotDefaults'

const loading = ref(false)
const rows = ref([])
// 只列出能当「全局默认过滤器」用的模板：scope 为 GLOBAL 或 BOTH（v2 模板，限制可填项）
const templates = ref([])

async function loadData() {
  loading.value = true
  try {
    rows.value = (await gatewayGlobalFilterApi.list()).data || []
  } finally {
    loading.value = false
  }
}
async function loadTemplates() {
  const r = await gatewayTemplateV2Api.templates('FILTER')
  templates.value = (r.data || []).filter((t) => t.status === 1 && (t.scope === 'GLOBAL' || t.scope === 'BOTH'))
}
onMounted(() => {
  loadData()
  loadTemplates()
})

function safeParse(s) {
  try {
    return JSON.parse(s || '[]')
  } catch {
    return {}
  }
}

const dlg = ref(false)
const saving = ref(false)
const currentId = ref(null)
const tree = ref(null)
const form = reactive({ filterName: '', args: {}, argsJsonRaw: '', sortNum: 0, enabled: 1, remark: '' })

const useV2 = computed(() => templates.value.some((t) => t.name === form.filterName))
const slots = computed(() => tree.value?.fields || [])

function mergeWithDefaults(slots, existing, target) {
  for (const slot of slots || []) {
    const k = slot.keyName
    const t = slot.valueType || {}
    if (!existing || !(k in existing) || existing[k] === undefined || existing[k] === null) {
      target[k] = defaultOf(slot)
      continue
    }
    const v = existing[k]
    if (t.kind === 'OBJECT' && v && typeof v === 'object' && !Array.isArray(v)) {
      target[k] = {}
      mergeWithDefaults(t.fields?.filter((f) => f.role === 'FIELD' || !f.role), v, target[k])
    } else {
      target[k] = v
    }
  }
  for (const k of Object.keys(existing || {})) {
    if (!(k in target)) target[k] = existing[k]
  }
}

async function loadTreeFor(name, existing) {
  const t = templates.value.find((x) => x.name === name)
  if (!t) {
    tree.value = null
    return
  }
  const r = await gatewayTemplateV2Api.templateTree(t.id)
  tree.value = r.data || null
  const next = {}
  mergeWithDefaults(tree.value?.fields || [], existing, next)
  form.args = next
}

function openCreate() {
  currentId.value = null
  Object.assign(form, { filterName: '', args: {}, argsJsonRaw: '', sortNum: rows.value.length + 1, enabled: 1, remark: '' })
  tree.value = null
  dlg.value = true
}
function openEdit(row) {
  currentId.value = row.id
  Object.assign(form, {
    filterName: row.filterName,
    args: safeParse(row.argsJson),
    argsJsonRaw: row.argsJson || '',
    sortNum: row.sortNum ?? 0,
    enabled: row.enabled ?? 1,
    remark: row.remark || '',
  })
  dlg.value = true
  if (templates.value.some((t) => t.name === form.filterName)) {
    loadTreeFor(form.filterName, form.args)
  } else {
    tree.value = null
  }
}
function onTemplateChange() {
  loadTreeFor(form.filterName)
}
async function serverValidate() {
  if (!useV2.value) return null
  const { data } = await gatewayTemplateV2Api.validate({
    kind: 'FILTER',
    name: form.filterName,
    valueJson: JSON.stringify(form.args || {}),
  })
  if (!data?.ok) {
    const errs = data?.errors || ['校验未通过']
    return Array.isArray(errs) ? errs[0] : String(errs)
  }
  return null
}
async function save() {
  if (!form.filterName) return ElMessage.warning('请选择过滤器模板')
  const err = await serverValidate()
  if (err) return ElMessage.warning(err)
  // 有 v2 模板走受限表单；找不到模板（历史手填数据）退回原始 JSON 编辑
  const argsJson = useV2.value ? JSON.stringify(form.args || {}) : (form.argsJsonRaw || '{}')
  saving.value = true
  try {
    const payload = {
      filterName: form.filterName,
      sortNum: Number(form.sortNum) || 0,
      enabled: form.enabled,
      remark: form.remark,
      argsJson,
    }
    if (currentId.value) await gatewayGlobalFilterApi.update(currentId.value, payload)
    else await gatewayGlobalFilterApi.create(payload)
    ElMessage.success('已保存')
    dlg.value = false
    loadData()
  } finally {
    saving.value = false
  }
}
async function remove(row) {
  if (!await isConfirmed(`删除全局过滤器 ${row.filterName}？`)) return
  await gatewayGlobalFilterApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}
async function toggleEnabled(row) {
  await gatewayGlobalFilterApi.update(row.id, { enabled: row.enabled })
  ElMessage.success('已更新')
}

// ---- 排序：上移 / 下移后重排编号 ----
async function renumber() {
  for (let i = 0; i < rows.value.length; i++) {
    const target = i + 1
    if (rows.value[i].sortNum !== target) {
      rows.value[i].sortNum = target
      await gatewayGlobalFilterApi.update(rows.value[i].id, { sortNum: target })
    }
  }
}
async function moveUp(i) {
  if (i <= 0) return
  const arr = rows.value
  const tmp = arr[i]
  arr[i] = arr[i - 1]
  arr[i - 1] = tmp
  await renumber()
}
async function moveDown(i) {
  const arr = rows.value
  if (i >= arr.length - 1) return
  const tmp = arr[i]
  arr[i] = arr[i + 1]
  arr[i + 1] = tmp
  await renumber()
}

function argsSummary(row) {
  const a = safeParse(row.argsJson)
  const entries = Object.entries(a)
  if (!entries.length) return '—'
  return entries.map(([k, v]) => `${k}=${v}`).join('  ')
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <div class="mb-4 flex items-center justify-between">
        <div>
          <span class="text-base font-bold">全局默认过滤器（default-filters）</span>
          <div class="text-xs text-gray-400 mt-1">自动挂到每条路由上；上移/下移调整执行顺序</div>
        </div>
        <el-button type="primary" @click="openCreate">添加过滤器</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="顺序" width="110">
          <template #default="{ row, $index }">
            <el-button-group size="small">
              <el-button :disabled="$index === 0" @click="moveUp($index)">↑</el-button>
              <el-button :disabled="$index === rows.length - 1" @click="moveDown($index)">↓</el-button>
            </el-button-group>
          </template>
        </el-table-column>
        <el-table-column prop="filterName" label="过滤器" width="180">
          <template #default="{ row }">
            <el-tag>{{ row.filterName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参数" min-width="280">
          <template #default="{ row }">
            <span class="text-xs text-gray-600">{{ argsSummary(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="currentId ? '编辑全局过滤器' : '添加全局过滤器'" width="640">
      <el-form label-width="100">
        <el-form-item label="过滤器" required>
          <el-select v-model="form.filterName" filterable placeholder="选择过滤器模板" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="t.id" :label="`${t.displayName}（${t.name}）`" :value="t.name" />
          </el-select>
        </el-form-item>

        <!-- v2 模板 → 受限表单（list/map/嵌套 object 均可，含提示与校验正则） -->
        <el-form-item v-if="useV2" label="参数">
          <div class="w-full">
            <div v-if="tree?.remark" class="text-xs text-gray-400 mb-2">{{ tree.remark }}</div>
            <ValueTreeForm v-model="form.args" :slots="slots" />
            <div class="text-xs text-gray-400 mt-1">保存时按模板做服务端校验（必填/正则/枚举/类型）</div>
          </div>
        </el-form-item>

        <!-- 无 v2 模板（历史手填数据）→ 直接编辑 JSON 兜底 -->
        <el-form-item v-else label="参数 JSON">
          <el-input v-model="form.argsJsonRaw" type="textarea" :rows="4" placeholder='{"name":"X-Request-Id"}' />
        </el-form-item>

        <el-form-item label="顺序">
          <el-input-number v-model="form.sortNum" :min="1" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
