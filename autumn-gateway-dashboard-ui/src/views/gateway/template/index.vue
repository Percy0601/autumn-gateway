<script setup>
// 模板管理（v2）：模板化可视化配置页面
//  页签1/2/3：过滤器/断言/结构化模板（模板定义 + 配置项元数据，内置只读、支持复制、自定义可编辑）
//  页签3 附：结构化全局配置实例（globalcors 等，ValueTreeForm 递归编辑 + 导出预览）
//  页签4：值类型目录（BASIC/LIST/OBJECT，复合类型结构编辑）
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayTemplateV2Api } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import FieldRowsEditor from '../components/FieldRowsEditor.vue'
import ValueTreeForm from '../components/ValueTreeForm.vue'
import TemplateTable from '../components/TemplateTable.vue'

const loading = ref(false)
const activeTab = ref('FILTER')
const keyword = ref('')

// ---------- 数据 ----------
const types = ref([]) // 值类型目录
const tpls = reactive({ FILTER: [], PREDICATE: [], STRUCT: [], CONFIG: [] })
const structs = ref([])
const profile = ref('dev')

const KIND_SCOPE = {
  FILTER: ['ROUTE', 'GLOBAL', 'BOTH'],
  PREDICATE: ['ROUTE'],
  STRUCT: ['GLOBAL'],
  CONFIG: ['GLOBAL'],
}
const SCOPE_TEXT = { ROUTE: '路由级', GLOBAL: '全局', BOTH: '两者' }

async function loadTypes() {
  const r = await gatewayTemplateV2Api.valueTypes()
  types.value = r.data || []
}
async function loadTemplates(kind) {
  const r = await gatewayTemplateV2Api.templates(kind)
  tpls[kind] = r.data || []
}
async function loadStructs() {
  const r = await gatewayTemplateV2Api.structs(profile.value)
  structs.value = r.data || []
}
onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadTypes(), loadTemplates('FILTER'), loadTemplates('PREDICATE'), loadTemplates('STRUCT'), loadTemplates('CONFIG'), loadStructs()])
  } finally {
    loading.value = false
  }
})

const currentRows = computed(() => {
  const arr = tpls[activeTab.value] || []
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return arr
  return arr.filter((t) => (t.name || '').toLowerCase().includes(kw) || (t.displayName || '').toLowerCase().includes(kw))
})
const filteredStructs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return structs.value
  return structs.value.filter((s) => (s.structName || '').toLowerCase().includes(kw) || (s.instanceName || '').toLowerCase().includes(kw))
})

const typeNameOf = (id) => {
  const t = types.value.find((x) => x.id === Number(id))
  return t ? t.displayName : `#${id}`
}
const builtinTag = (b) => (b === 1 ? { text: '内置', type: 'info' } : { text: '自定义', type: 'success' })

// ---------- 模板编辑 ----------
const tplDlg = ref(false)
const tplSaving = ref(false)
const tplMode = ref('create') // create | edit | copy
const tplForm = reactive({
  kind: 'FILTER', name: '', displayName: '', category: '', scope: 'ROUTE', configGroup: '',
  singletonPerRoute: 0, sortNum: 1, status: 1, remark: '',
})
const tplFields = ref([])
const tplEditId = ref(null)

function openTplCreate(kind) {
  tplMode.value = 'create'
  tplEditId.value = null
  Object.assign(tplForm, { kind, name: '', displayName: '', category: '', scope: kind === 'PREDICATE' ? 'ROUTE' : (kind === 'STRUCT' || kind === 'CONFIG') ? 'GLOBAL' : 'ROUTE', configGroup: kind === 'CONFIG' ? '' : '', singletonPerRoute: 0, sortNum: nextSort(kind), status: 1, remark: '' })
  tplFields.value = []
  tplDlg.value = true
}
function openTplEdit(row) {
  if (row.builtin === 1) return ElMessage.info('内置模板只读，请使用「复制」后编辑')
  tplMode.value = 'edit'
  tplEditId.value = row.id
  Object.assign(tplForm, pickDef(row))
  tplFields.value = (row.fields || []).map((f) => stripMeta(f))
  tplDlg.value = true
}
async function openTplCopy(row) {
  const tree = (await gatewayTemplateV2Api.templateTree(row.id)).data
  tplMode.value = 'copy'
  tplEditId.value = null
  Object.assign(tplForm, pickDef(tree))
  tplForm.name = `${tree.name}-copy`
  tplForm.builtin = 0
  tplFields.value = (tree.fields || []).map((f) => stripMeta(f))
  tplDlg.value = true
}
const pickDef = (t) => ({
  kind: t.kind, name: t.name, displayName: t.displayName, category: t.category || '',
  scope: t.scope, configGroup: t.configGroup || '', singletonPerRoute: t.singletonPerRoute ?? 0,
  sortNum: t.sortNum ?? 1, status: t.status ?? 1, remark: t.remark || '',
})
const stripMeta = (f) => ({
  keyName: f.keyName, label: f.label || '', valueTypeId: f.valueTypeId,
  required: f.required ?? 0, defaultValue: f.defaultValue ?? '', optionsJson: f.optionsJson ?? '',
  placeholder: f.placeholder ?? '', validator: f.validator ?? '', sensitive: f.sensitive ?? 0,
  isMapKey: f.isMapKey ?? 0, mapKeyValidator: f.mapKeyValidator ?? '', exportKey: f.exportKey ?? '',
  sortNum: f.sortNum ?? 1, remark: f.remark || '',
})
const nextSort = (kind) => (tpls[kind].length || 0) + 1

async function saveTpl() {
  if (!tplForm.name.trim()) return ElMessage.warning('模板名不能为空')
  if (!tplForm.displayName.trim()) return ElMessage.warning('显示名不能为空')
  if (tplForm.kind === 'CONFIG' && !tplForm.configGroup) return ElMessage.warning('全局配置模板需要选择配置分组')
  if (!tplFields.value.length) return ElMessage.warning('至少配置 1 个配置项')
  if (tplFields.value.some((f) => !f.keyName?.trim())) return ElMessage.warning('存在未填写 Key 的配置项')
  if (tplFields.value.some((f) => !f.valueTypeId)) return ElMessage.warning('存在未选择值类型的配置项')
  tplSaving.value = true
  try {
    const payload = {
      def: { ...tplForm, scope: KIND_SCOPE[tplForm.kind].includes(tplForm.scope) ? tplForm.scope : KIND_SCOPE[tplForm.kind][0] },
      fields: tplFields.value.map((f, i) => ({ ...f, sortNum: f.sortNum ?? i + 1 })),
    }
    if (tplMode.value === 'edit' && tplEditId.value) {
      await gatewayTemplateV2Api.updateTemplate(tplEditId.value, payload)
    } else {
      await gatewayTemplateV2Api.createTemplate(payload)
    }
    ElMessage.success(tplMode.value === 'copy' ? '已复制为新模板' : '已保存')
    tplDlg.value = false
    await loadTemplates(tplForm.kind)
  } finally {
    tplSaving.value = false
  }
}
async function removeTpl(row) {
  if (!(await isConfirmed(`删除模板 ${row.displayName}（${row.name}）？`))) return
  await gatewayTemplateV2Api.removeTemplate(row.id)
  ElMessage.success('已删除')
  await loadTemplates(activeTab.value)
}

// ---------- 值类型编辑 ----------
const typeDlg = ref(false)
const typeSaving = ref(false)
const typeMode = ref('create') // create | edit | copy
const typeForm = reactive({ name: '', displayName: '', kind: 'BASIC', sortNum: 1, status: 1, remark: '' })
const typeFields = ref([])
const typeEditId = ref(null)

const showTypeFields = computed(() => typeForm.kind === 'LIST' || typeForm.kind === 'OBJECT')

function openTypeCreate() {
  typeMode.value = 'create'
  typeEditId.value = null
  Object.assign(typeForm, { name: '', displayName: '', kind: 'BASIC', sortNum: types.value.length + 1, status: 1, remark: '' })
  typeFields.value = []
  typeDlg.value = true
}
function openTypeEdit(row) {
  if (row.builtin === 1) return ElMessage.info('内置类型只读，请使用「复制」后编辑')
  typeMode.value = 'edit'
  typeEditId.value = row.id
  Object.assign(typeForm, pickType(row))
  typeFields.value = (row.fields || []).map((f) => stripMeta(f))
  typeDlg.value = true
}
function openTypeCopy(row) {
  typeMode.value = 'copy'
  typeEditId.value = null
  Object.assign(typeForm, pickType(row))
  typeForm.name = `${row.name}-copy`
  typeForm.builtin = 0
  typeFields.value = (row.fields || []).map((f) => stripMeta(f))
  typeDlg.value = true
}
const pickType = (t) => ({
  name: t.name, displayName: t.displayName, kind: t.kind,
  sortNum: t.sortNum ?? 1, status: t.status ?? 1, remark: t.remark || '',
})

async function saveType() {
  if (!typeForm.name.trim()) return ElMessage.warning('类型名不能为空')
  if (typeForm.kind === 'LIST' && typeFields.value.length !== 1) return ElMessage.warning('LIST 类型必须恰好 1 个元素定义')
  if (typeForm.kind === 'OBJECT' && !typeFields.value.length) return ElMessage.warning('OBJECT 类型至少 1 个字段')
  if (typeFields.value.some((f) => !f.valueTypeId)) return ElMessage.warning('存在未选择值类型的字段')
  typeSaving.value = true
  try {
    let id = typeEditId.value
    if (typeMode.value === 'edit' && id) {
      await gatewayTemplateV2Api.updateValueType(id, { ...typeForm })
    } else {
      const r = await gatewayTemplateV2Api.createValueType({ ...typeForm })
      id = r.data.id
    }
    if (showTypeFields.value) {
      await gatewayTemplateV2Api.saveTypeFields(id, typeFields.value.map((f, i) => ({ ...f, sortNum: f.sortNum ?? i + 1 })))
    }
    ElMessage.success(typeMode.value === 'copy' ? '已复制为新类型' : '已保存')
    typeDlg.value = false
    await loadTypes()
  } finally {
    typeSaving.value = false
  }
}
async function removeType(row) {
  if (!(await isConfirmed(`删除类型 ${row.displayName}（${row.name}）？`))) return
  await gatewayTemplateV2Api.removeValueType(row.id)
  ElMessage.success('已删除')
  await loadTypes()
}

// ---------- STRUCT 实例 ----------
const structDlg = ref(false)
const structSaving = ref(false)
const structEditId = ref(null)
const structForm = reactive({ profileCode: 'dev', structName: '', instanceName: '', sortNum: 1, status: 1, remark: '' })
const structSlots = ref([]) // 模板树顶层配置项（含递归 valueType）
const structValue = ref({})

const structTemplates = computed(() => tpls.STRUCT || [])

// 与 ValueTreeForm 相同的初始值逻辑（新建实例时预填默认值）
const vtOf = (slot) => slot?.valueType || {}
function buildDefault(slot) {
  const t = vtOf(slot)
  if (slot.isMapKey === 1) return {}
  if (t.kind === 'LIST') return []
  if (t.kind === 'OBJECT') {
    const o = {}
    ;(t.fields || []).filter((f) => f.role === 'FIELD' || !f.role).forEach((f) => {
      o[f.keyName] = buildDefault(f)
    })
    return o
  }
  const dv = slot.defaultValue
  if (dv !== undefined && dv !== null && dv !== '') {
    if (['INT', 'LONG', 'DOUBLE'].includes(t.name)) return Number(dv)
    if (t.name === 'BOOL') return dv === true || dv === 'true'
    return String(dv)
  }
  if (t.name === 'BOOL') return false
  if (['INT', 'LONG', 'DOUBLE'].includes(t.name)) return undefined
  return ''
}

async function onStructTemplateChange() {
  const t = structTemplates.value.find((x) => x.name === structForm.structName)
  if (!t) return
  const tree = (await gatewayTemplateV2Api.templateTree(t.id)).data
  structSlots.value = tree.fields || []
  const obj = {}
  structSlots.value.forEach((slot) => {
    obj[slot.keyName] = buildDefault(slot)
  })
  structValue.value = obj
}
function openStructCreate() {
  structEditId.value = null
  Object.assign(structForm, { profileCode: profile.value, structName: '', instanceName: '', sortNum: structs.value.length + 1, status: 1, remark: '' })
  structSlots.value = []
  structValue.value = {}
  structDlg.value = true
}
async function openStructEdit(row) {
  structEditId.value = row.id
  Object.assign(structForm, {
    profileCode: row.profileCode, structName: row.structName, instanceName: row.instanceName,
    sortNum: row.sortNum ?? 1, status: row.status ?? 1, remark: row.remark || '',
  })
  const t = structTemplates.value.find((x) => x.name === row.structName)
  if (t) {
    const tree = (await gatewayTemplateV2Api.templateTree(t.id)).data
    structSlots.value = tree.fields || []
  }
  try {
    structValue.value = JSON.parse(row.valueJson || '{}')
  } catch {
    structValue.value = {}
  }
  structDlg.value = true
}
async function saveStruct() {
  if (!structForm.structName) return ElMessage.warning('请选择结构化模板')
  if (!structForm.instanceName.trim()) return ElMessage.warning('实例名不能为空')
  structSaving.value = true
  try {
    const payload = {
      profileCode: structForm.profileCode,
      structName: structForm.structName,
      instanceName: structForm.instanceName,
      valueJson: JSON.stringify(structValue.value || {}),
      sortNum: structForm.sortNum,
      status: structForm.status,
      remark: structForm.remark,
    }
    if (structEditId.value) await gatewayTemplateV2Api.updateStruct(structEditId.value, payload)
    else await gatewayTemplateV2Api.createStruct(payload)
    ElMessage.success('已保存')
    structDlg.value = false
    await loadStructs()
  } finally {
    structSaving.value = false
  }
}
async function removeStruct(row) {
  if (!(await isConfirmed(`删除结构化实例 ${row.instanceName}？`))) return
  await gatewayTemplateV2Api.removeStruct(row.id)
  ElMessage.success('已删除')
  await loadStructs()
}

// ---------- 导出预览 ----------
const previewDlg = ref(false)
const previewTitle = ref('')
const previewLines = ref([])
async function showPreview(row) {
  const r = await gatewayTemplateV2Api.structPreview(row.id)
  previewTitle.value = `${row.instanceName}（${row.structName}）`
  previewLines.value = r.data || []
  previewDlg.value = true
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <div class="mb-4 flex items-center justify-between">
        <div>
          <span class="text-base font-bold">模板管理（v2）</span>
          <div class="text-xs text-gray-400 mt-1">
            可视化配置不再手填 key/value：模板定义配置项提示、值类型目录驱动递归表单，覆盖标量 / list / map / 嵌套对象；内置模板只读，可复制为自定义
          </div>
        </div>
        <div class="flex items-center gap-2">
          <el-input v-model="keyword" placeholder="搜索名称" clearable style="width: 200px" />
          <el-button v-if="activeTab === 'FILTER' || activeTab === 'PREDICATE' || activeTab === 'CONFIG'" type="primary" @click="openTplCreate(activeTab)">新建模板</el-button>
          <el-button v-else-if="activeTab === 'STRUCT'" type="primary" @click="openStructCreate">新建实例</el-button>
          <el-button v-else type="primary" @click="openTypeCreate">新建值类型</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" v-loading="loading">
        <!-- ============ 过滤器 / 断言模板 ============ -->
        <el-tab-pane label="过滤器模板" name="FILTER">
          <template v-if="activeTab === 'FILTER'">
            <TemplateTable :rows="currentRows" @copy="openTplCopy" @edit="openTplEdit" @remove="removeTpl" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="断言模板" name="PREDICATE">
          <template v-if="activeTab === 'PREDICATE'">
            <TemplateTable :rows="currentRows" @copy="openTplCopy" @edit="openTplEdit" @remove="removeTpl" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="结构化模板" name="STRUCT">
          <TemplateTable :rows="currentRows" @copy="openTplCopy" @edit="openTplEdit" @remove="removeTpl" />

          <!-- 实例 -->
          <div class="mb-3 mt-2 text-sm font-semibold">结构化全局配置实例（profile: dev）</div>
          <el-table :data="filteredStructs" border size="small">
            <el-table-column prop="instanceName" label="实例名" min-width="140">
              <template #default="{ row }">
                <span class="font-semibold">{{ row.instanceName }}</span>
                <div class="text-xs text-gray-400">{{ row.structName }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="profileCode" label="环境" width="80" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openStructEdit(row)">编辑</el-button>
                <el-button size="small" @click="showPreview(row)">导出预览</el-button>
                <el-button size="small" type="danger" @click="removeStruct(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 全局 KV 配置组模板 ============ -->
        <el-tab-pane label="全局配置模板" name="CONFIG">
          <div class="mb-3 text-xs text-gray-400">
            全局 KV 配置组模板：name=配置前缀（如 autumn.authn），配置项 export_key 可为点号多段相对路径，
            在「网关全局配置」页可从模板添加生成 key/value；敏感项自动加密；只支持标量/列表值类型（嵌套对象走结构化模板）。
          </div>
          <TemplateTable :rows="currentRows" @copy="openTplCopy" @edit="openTplEdit" @remove="removeTpl" />
        </el-tab-pane>

        <!-- ============ 值类型目录 ============ -->
        <el-tab-pane label="值类型目录" name="TYPE">
          <el-table :data="types" border size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="displayName" label="显示名" min-width="130">
              <template #default="{ row }">
                <span class="font-semibold">{{ row.displayName }}</span>
                <div class="text-xs text-gray-400">{{ row.name }}</div>
              </template>
            </el-table-column>
            <el-table-column label="种类" width="90">
              <template #default="{ row }">
                <el-tag :type="row.kind === 'BASIC' ? 'info' : row.kind === 'LIST' ? 'success' : 'warning'" size="small">
                  {{ row.kind === 'BASIC' ? '标量' : row.kind === 'LIST' ? '列表' : '对象' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内置" width="80">
              <template #default="{ row }"><el-tag :type="builtinTag(row.builtin).type" size="small">{{ builtinTag(row.builtin).text }}</el-tag></template>
            </el-table-column>
            <el-table-column label="结构" width="90">
              <template #default="{ row }">{{ (row.fields || []).length }} 项</template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openTypeCopy(row)">复制</el-button>
                <el-button size="small" :disabled="row.builtin === 1" @click="openTypeEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" :disabled="row.builtin === 1" @click="removeType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <!-- ============ 模板编辑对话框 ============ -->
      <el-dialog v-model="tplDlg" :title="tplMode === 'create' ? '新建模板' : tplMode === 'copy' ? '复制模板' : '编辑模板'" width="980" top="6vh">
        <el-form label-width="90">
          <div class="grid grid-cols-3 gap-3">
            <el-form-item label="名称" required>
              <el-input v-model="tplForm.name" placeholder="工厂名，如 AddRequestHeader" :disabled="tplMode === 'edit'" />
            </el-form-item>
            <el-form-item label="显示名" required>
              <el-input v-model="tplForm.displayName" placeholder="如 添加请求头" />
            </el-form-item>
            <el-form-item label="分类">
              <el-input v-model="tplForm.category" placeholder="如 头操作" />
            </el-form-item>
            <el-form-item label="作用域">
              <el-select v-model="tplForm.scope" class="w-full" :disabled="tplForm.kind !== 'FILTER'">
                <el-option v-for="s in KIND_SCOPE[tplForm.kind]" :key="s" :label="SCOPE_TEXT[s]" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="tplForm.kind === 'CONFIG'" label="配置分组" required>
              <el-select v-model="tplForm.configGroup" class="w-full" placeholder="落 gw_config_item 的分组">
                <el-option v-for="g in ['SERVER','DISCOVERY','AUTHN','AUTHZ','LIMIT','SCG','CORS','CUSTOM']" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="每路由单例">
              <el-switch v-model="tplForm.singletonPerRoute" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="状态">
              <el-switch v-model="tplForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="tplForm.sortNum" :min="1" />
            </el-form-item>
            <el-form-item label="备注" class="col-span-2">
              <el-input v-model="tplForm.remark" type="textarea" :rows="1" />
            </el-form-item>
          </div>
          <el-divider content-position="left">配置项（实例表单由值类型自动生成）</el-divider>
          <FieldRowsEditor v-model="tplFields" :types="types" />
        </el-form>
        <template #footer>
          <el-button @click="tplDlg = false">取消</el-button>
          <el-button type="primary" :loading="tplSaving" @click="saveTpl">保存</el-button>
        </template>
      </el-dialog>

      <!-- ============ 值类型编辑对话框 ============ -->
      <el-dialog v-model="typeDlg" :title="typeMode === 'create' ? '新建值类型' : typeMode === 'copy' ? '复制值类型' : '编辑值类型'" width="980" top="6vh">
        <el-form label-width="90">
          <div class="grid grid-cols-3 gap-3">
            <el-form-item label="类型名" required>
              <el-input v-model="typeForm.name" placeholder="如 OBJECT_CORS / LIST_STRING" :disabled="typeMode === 'edit'" />
            </el-form-item>
            <el-form-item label="显示名" required>
              <el-input v-model="typeForm.displayName" placeholder="如 CORS 规则对象" />
            </el-form-item>
            <el-form-item label="种类">
              <el-select v-model="typeForm.kind" class="w-full" :disabled="typeMode !== 'create'">
                <el-option label="标量 BASIC" value="BASIC" />
                <el-option label="列表 LIST" value="LIST" />
                <el-option label="对象 OBJECT" value="OBJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="typeForm.sortNum" :min="1" />
            </el-form-item>
            <el-form-item label="状态">
              <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="typeForm.remark" />
            </el-form-item>
          </div>
          <template v-if="showTypeFields">
            <el-divider content-position="left">{{ typeForm.kind === 'LIST' ? '元素类型（ITEM）' : '字段定义（FIELD）' }}</el-divider>
            <FieldRowsEditor v-model="typeFields" :types="types" :list-mode="typeForm.kind === 'LIST'" />
          </template>
        </el-form>
        <template #footer>
          <el-button @click="typeDlg = false">取消</el-button>
          <el-button type="primary" :loading="typeSaving" @click="saveType">保存</el-button>
        </template>
      </el-dialog>

      <!-- ============ STRUCT 实例编辑对话框 ============ -->
      <el-dialog v-model="structDlg" :title="structEditId ? '编辑结构化实例' : '新建结构化实例'" width="760" top="5vh">
        <el-form label-width="100">
          <div class="grid grid-cols-2 gap-3">
            <el-form-item label="结构化模板" required>
              <el-select v-model="structForm.structName" class="w-full" filterable :disabled="!!structEditId" @change="onStructTemplateChange">
                <el-option v-for="t in structTemplates" :key="t.id" :label="`${t.displayName}（${t.name}）`" :value="t.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="实例名" required>
              <el-input v-model="structForm.instanceName" placeholder="如 全局跨域示例" />
            </el-form-item>
            <el-form-item label="环境">
              <el-input v-model="structForm.profileCode" placeholder="dev / prod" />
            </el-form-item>
            <el-form-item label="状态">
              <el-switch v-model="structForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="structForm.sortNum" :min="1" />
            </el-form-item>
            <el-form-item label="备注" class="col-span-2">
              <el-input v-model="structForm.remark" type="textarea" :rows="1" />
            </el-form-item>
          </div>
          <el-divider content-position="left">配置值（{{ structForm.structName || '选择模板后生成表单' }}）</el-divider>
          <div v-if="structSlots.length" class="border rounded p-2">
            <ValueTreeForm v-model="structValue" :slots="structSlots" />
          </div>
          <el-empty v-else description="请先选择结构化模板" :image-size="60" />
        </el-form>
        <template #footer>
          <el-button @click="structDlg = false">取消</el-button>
          <el-button type="primary" :loading="structSaving" @click="saveStruct">保存（服务端校验）</el-button>
        </template>
      </el-dialog>

      <!-- ============ 导出预览 ============ -->
      <el-dialog v-model="previewDlg" :title="`导出预览：${previewTitle}`" width="760" top="8vh">
        <div class="text-xs text-gray-400 mb-2">STRUCT 实例导出为 Spring properties 行（map 键 ['k'] / 列表 [i] / 对象点号展开）</div>
        <el-input :model-value="previewLines.join('\n')" type="textarea" :rows="14" readonly />
        <template #footer>
          <el-button type="primary" @click="previewDlg = false">关闭</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
