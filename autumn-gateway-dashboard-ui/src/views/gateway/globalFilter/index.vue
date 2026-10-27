<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayGlobalFilterApi, gatewayTemplateApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import ArgsForm from '../components/ArgsForm.vue'

const loading = ref(false)
const rows = ref([])
// 只列出能当「全局默认过滤器」用的模板：scope 为 GLOBAL 或 BOTH
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
  const r = await gatewayTemplateApi.filterList()
  templates.value = (r.data || []).filter((t) => t.scope === 'GLOBAL' || t.scope === 'BOTH')
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
const form = reactive({ filterName: '', args: {}, argsJsonRaw: '', sortNum: 0, enabled: 1, remark: '' })

const currentSchema = computed(() => {
  const t = templates.value.find((x) => x.filterName === form.filterName)
  return t ? safeParse(t.argsSchemaJson) : []
})

function openCreate() {
  currentId.value = null
  Object.assign(form, { filterName: '', args: {}, argsJsonRaw: '', sortNum: rows.value.length + 1, enabled: 1, remark: '' })
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
}
function onTemplateChange() {
  // 用户主动切换模板时，用模板默认值重置参数
  form.args = {}
  for (const f of currentSchema.value) {
    if (f.default !== undefined) form.args[f.key] = f.default
  }
}
async function save() {
  if (!form.filterName) return ElMessage.warning('请选择过滤器模板')
  for (const f of currentSchema.value) {
    if (f.required && (form.args[f.key] === undefined || form.args[f.key] === '')) {
      return ElMessage.warning(`参数 ${f.label || f.key} 必填`)
    }
  }
  // 有模板走参数表单，找不到模板（历史手填数据）退回原始 JSON 编辑
  const argsJson = currentSchema.value.length ? JSON.stringify(form.args || {}) : (form.argsJsonRaw || '{}')
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
      <div class="mb-3 flex items-center justify-between">
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

    <el-dialog v-model="dlg" :title="currentId ? '编辑全局过滤器' : '添加全局过滤器'" width="520">
      <el-form label-width="100">
        <el-form-item label="过滤器" required>
          <el-select v-model="form.filterName" filterable placeholder="选择过滤器模板" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="t.id" :label="`${t.displayName}（${t.filterName}）`" :value="t.filterName" />
          </el-select>
        </el-form-item>

        <ArgsForm v-if="currentSchema.length" v-model="form.args" :schema="currentSchema" />
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
