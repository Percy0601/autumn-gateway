<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayRouteApi, gatewayCenterApi, gatewayTemplateV2Api } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import ValueTreeForm from '../components/ValueTreeForm.vue'
import { buildSlotDefaults, defaultOf } from '../components/slotDefaults'

const loading = ref(false)
const rows = ref([])
const centers = ref([])
const filterTemplates = ref([])
const predicateTemplates = ref([])

async function loadData() {
  loading.value = true
  try {
    rows.value = (await gatewayRouteApi.list()).data || []
  } finally {
    loading.value = false
  }
}
// v2 模板驱动：过滤器/断言新增与编辑都由模板（gw_tpl_definition）限制可填项
async function loadDicts() {
  const [c, f, p] = await Promise.all([
    gatewayCenterApi.list(),
    gatewayTemplateV2Api.templates('FILTER'),
    gatewayTemplateV2Api.templates('PREDICATE'),
  ])
  centers.value = c.data || []
  filterTemplates.value = (f.data || []).filter((t) => t.status === 1)
  predicateTemplates.value = (p.data || []).filter((t) => t.status === 1)
}
onMounted(() => {
  loadData()
  loadDicts()
})

function safeParse(s) {
  try {
    return JSON.parse(s || '[]')
  } catch {
    return {}
  }
}

// ==================== 左侧：路由列表 / 右侧：当前路由详情 ====================
const current = ref(null)
const savingRoute = ref(false)
const serviceOptions = ref([])
const loadingServices = ref(false)

function selectRoute(row) {
  current.value = row
  serviceOptions.value = []
  loadSub()
}
function openCreate() {
  current.value = {
    id: null, routeCode: '', routeName: '', centerId: null, namespace: '',
    serviceName: '', uri: '', orderNum: 0, status: 1, remark: '',
  }
  subRows.value = []
}
async function onCenterChange() {
  serviceOptions.value = []
  if (!current.value?.centerId) return
  loadingServices.value = true
  try {
    const r = await gatewayCenterApi.services(current.value.centerId)
    serviceOptions.value = r.data || []
  } catch (e) {
    ElMessage.warning('拉取服务列表失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loadingServices.value = false
  }
}
function onServiceChange() {
  if (current.value?.serviceName) current.value.uri = 'lb://' + current.value.serviceName
}
async function saveRoute() {
  const r = current.value
  if (!r) return
  if (!r.routeCode || !r.routeName) return ElMessage.warning('路由编码/名称必填')
  if (!r.uri) return ElMessage.warning('URI 必填（选服务自动填 lb://，或手填 http://...）')
  savingRoute.value = true
  try {
    const payload = { ...r, orderNum: Number(r.orderNum) || 0 }
    if (r.id) await gatewayRouteApi.update(r.id, payload)
    else await gatewayRouteApi.create(payload)
    ElMessage.success('已保存')
    const code = r.routeCode
    await loadData()
    const found = rows.value.find((x) => x.routeCode === code)
    if (found) selectRoute(found)
  } finally {
    savingRoute.value = false
  }
}
async function removeRoute() {
  const r = current.value
  if (!r || !r.id) return
  if (!await isConfirmed(`删除路由 ${r.routeCode}？`)) return
  await gatewayRouteApi.remove(r.id)
  ElMessage.success('已删除')
  current.value = null
  loadData()
}

// ==================== 断言 / 过滤器（右侧 Tab，v2 模板驱动） ====================
const tab = ref('filter')
const subRows = ref([])
const subEditOpen = ref(false)
const subSaving = ref(false)
const subForm = reactive({ name: '', sortNum: 0, args: {} })
const subTree = ref(null) // 当前选中模板的递归结构（含 valueType 子树）
const legacyJson = ref('') // 无 v2 模板的旧数据兜底：直接编辑 JSON

const subTemplates = computed(() => (tab.value === 'filter' ? filterTemplates.value : predicateTemplates.value))
const subUseV2 = computed(() => subTemplates.value.some((t) => t.name === subForm.name))
const subSlots = computed(() => subTree.value?.fields || [])
// singleton 模板已挂载则下拉置灰（编辑自身时不禁用）
const mountedSingletons = computed(() => {
  const key = subNameField()
  const mounted = new Set(subRows.value.map((r) => r[key]).filter(Boolean))
  const disabled = new Set()
  for (const t of subTemplates.value) {
    if (t.singletonPerRoute === 1 && mounted.has(t.name) && t.name !== subForm.name) disabled.add(t.name)
  }
  return disabled
})
function tplLabel(t) {
  return `${t.displayName || t.name}（${t.name}）`
}
const subNameField = () => (tab.value === 'filter' ? 'filterName' : 'predicateName')
const subKind = () => (tab.value === 'filter' ? 'FILTER' : 'PREDICATE')
const subApi = () =>
  tab.value === 'filter'
    ? { list: gatewayRouteApi.filters, add: gatewayRouteApi.addFilter, update: gatewayRouteApi.updateFilter, remove: gatewayRouteApi.removeFilter }
    : { list: gatewayRouteApi.predicates, add: gatewayRouteApi.addPredicate, update: gatewayRouteApi.updatePredicate, remove: gatewayRouteApi.removePredicate }

// 用模板默认值打底，再把已有实例值合并进来（编辑旧数据时尽量保留）
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

async function loadSub() {
  if (!current.value?.id) {
    subRows.value = []
    return
  }
  const r = await subApi().list(current.value.id)
  subRows.value = (r.data || []).map((x) => ({ ...x, args: safeParse(x.argsJson) }))
}
function onTabChange() {
  loadSub()
}
function openSubCreate() {
  Object.assign(subForm, { name: '', sortNum: subRows.value.length + 1, args: {} })
  subTree.value = null
  legacyJson.value = ''
  subEditOpen.value = true
}
function openSubEdit(row) {
  Object.assign(subForm, {
    name: row[subNameField()],
    sortNum: row.sortNum ?? 0,
    args: { ...(row.args || {}) },
  })
  legacyJson.value = row.argsJson || '{}'
  subEditOpen.value = true
  if (subTemplates.value.some((t) => t.name === subForm.name)) {
    loadTreeFor(subForm.name, row.args)
  }
}
async function onSubTemplateChange() {
  await loadTreeFor(subForm.name)
}
async function loadTreeFor(name, existing) {
  const t = subTemplates.value.find((x) => x.name === name)
  if (!t) {
    subTree.value = null
    return
  }
  const r = await gatewayTemplateV2Api.templateTree(t.id)
  subTree.value = r.data || null
  const next = {}
  mergeWithDefaults(subTree.value?.fields || [], existing, next)
  subForm.args = next
}
// 服务端按模板校验（v2 权威校验：必填/正则/枚举/类型）
async function serverValidate() {
  if (!subUseV2.value) return null
  const { data } = await gatewayTemplateV2Api.validate({
    kind: subKind(),
    name: subForm.name,
    valueJson: JSON.stringify(subForm.args || {}),
  })
  if (!data?.ok) {
    const errs = data?.errors || ['校验未通过']
    return Array.isArray(errs) ? errs[0] : String(errs)
  }
  return null
}
async function saveSub() {
  if (!current.value?.id) return
  if (!subForm.name) return ElMessage.warning('请选择模板')
  const tpl = subTemplates.value.find((t) => t.name === subForm.name)
  if (tpl?.singletonPerRoute === 1 && subRows.value.some((r) => r[subNameField()] === subForm.name)) {
    return ElMessage.warning(`${tpl.displayName || tpl.name} 每路由只允许挂一次`)
  }
  const err = await serverValidate()
  if (err) return ElMessage.warning(err)
  subSaving.value = true
  try {
    const payload = {
      [subNameField()]: subForm.name,
      sortNum: Number(subForm.sortNum) || 0,
      argsJson: subUseV2.value ? JSON.stringify(subForm.args || {}) : (legacyJson.value || '{}'),
    }
    await subApi().add(current.value.id, payload)
    ElMessage.success('已添加')
    subEditOpen.value = false
    loadSub()
  } finally {
    subSaving.value = false
  }
}
async function saveSubEdit() {
  // 编辑场景：只改参数与顺序（模板名一般不变，允许改参数）
  const row = subRows.value.find((x) => x.id === subEditingId.value)
  if (!row) return
  const err = await serverValidate()
  if (err) return ElMessage.warning(err)
  subSaving.value = true
  try {
    await subApi().update(row.id, {
      [subNameField()]: subForm.name,
      sortNum: Number(subForm.sortNum) || 0,
      argsJson: subUseV2.value ? JSON.stringify(subForm.args || {}) : (legacyJson.value || '{}'),
    })
    ElMessage.success('已保存')
    subEditOpen.value = false
    loadSub()
  } finally {
    subSaving.value = false
  }
}
async function removeSub(row) {
  if (!await isConfirmed('删除该条？')) return
  await subApi().remove(row.id)
  ElMessage.success('已删除')
  loadSub()
}
const subEditingId = ref(null)
function openSubCreateDialog() {
  subEditingId.value = null
  openSubCreate()
}
function openSubEditDialog(row) {
  subEditingId.value = row.id
  openSubEdit(row)
}
function submitSub() {
  return subEditingId.value ? saveSubEdit() : saveSub()
}

// ---- 子项排序：上移 / 下移后重排 ----
async function renumberSub() {
  const api = subApi().update
  for (let i = 0; i < subRows.value.length; i++) {
    const target = i + 1
    if (subRows.value[i].sortNum !== target) {
      subRows.value[i].sortNum = target
      await api(subRows.value[i].id, { sortNum: target })
    }
  }
}
async function moveSubUp(i) {
  if (i <= 0) return
  const arr = subRows.value
  const tmp = arr[i]
  arr[i] = arr[i - 1]
  arr[i - 1] = tmp
  await renumberSub()
}
async function moveSubDown(i) {
  const arr = subRows.value
  if (i >= arr.length - 1) return
  const tmp = arr[i]
  arr[i] = arr[i + 1]
  arr[i + 1] = tmp
  await renumberSub()
}

function centerName(id) {
  return centers.value.find((c) => c.id === id)?.centerName || '—'
}
function subArgsSummary(row) {
  const a = safeParse(row.argsJson)
  const entries = Object.entries(a)
  if (!entries.length) return '—'
  return entries
    .map(([k, v]) => `${k}=${Array.isArray(v) ? `[${v.join(', ')}]` : v}`)
    .join('  ')
}
</script>

<template>
  <div class="p-4 flex gap-3" style="height: calc(100vh - 120px)">
    <!-- 左侧：路由列表 -->
    <el-card class="shrink-0" style="width: 340px" shadow="never">
      <div class="mb-4 flex items-center justify-between">
        <span class="font-bold">路由列表</span>
        <el-button type="primary" size="small" @click="openCreate">新增</el-button>
      </div>
      <div v-loading="loading" class="route-list">
        <div
          v-for="r in rows"
          :key="r.id"
          class="route-item"
          :class="{ active: current && current.id === r.id }"
          @click="selectRoute(r)"
        >
          <div class="flex items-center justify-between">
            <span class="font-medium truncate">{{ r.routeName }}</span>
            <el-tag :type="r.status === 1 ? 'success' : 'info'" size="small">{{ r.status === 1 ? '启用' : '停用' }}</el-tag>
          </div>
          <div class="text-xs text-gray-400 mt-1">{{ r.routeCode }}</div>
          <div class="text-xs text-gray-400 truncate">{{ r.uri }}</div>
        </div>
        <el-empty v-if="!rows.length" description="暂无路由，点击右上角新增" :image-size="60" />
      </div>
    </el-card>

    <!-- 右侧：路由详情 + 断言/过滤器 -->
    <el-card class="flex-1" shadow="never">
      <template v-if="current">
        <div class="mb-4 font-bold">{{ current.id ? '路由详情' : '新增路由' }}</div>
        <el-form label-width="90">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="路由编码" required><el-input v-model="current.routeCode" placeholder="如 order-service" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="名称" required><el-input v-model="current.routeName" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="配置中心">
                <el-select v-model="current.centerId" clearable filterable placeholder="选了才能从注册中心拉服务" @change="onCenterChange">
                  <el-option v-for="c in centers" :key="c.id" :label="c.centerName" :value="c.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="已注册服务">
                <el-select v-model="current.serviceName" clearable filterable :loading="loadingServices" :disabled="!current.centerId" placeholder="选服务自动填 lb://，也可手填 URI" @change="onServiceChange">
                  <el-option v-for="s in serviceOptions" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="current.centerId" :span="12">
              <el-form-item label="命名空间">
                <el-input v-model="current.namespace" placeholder="Nacos 用；Consul 留空" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="URI" required><el-input v-model="current.uri" placeholder="lb://service 或 http://host:port" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="顺序"><el-input-number v-model="current.orderNum" :min="0" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-switch v-model="current.status" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注"><el-input v-model="current.remark" type="textarea" :rows="2" /></el-form-item>
            </el-col>
          </el-row>
          <div class="flex justify-end gap-2 mb-3">
            <el-button type="danger" plain :disabled="!current.id" @click="removeRoute">删除</el-button>
            <el-button type="primary" :loading="savingRoute" @click="saveRoute">保存路由</el-button>
          </div>
        </el-form>

        <!-- 断言 / 过滤器 -->
        <el-tabs v-model="tab" class="mt-4" @tab-change="onTabChange">
          <el-tab-pane label="过滤器" name="filter">
            <div class="mb-3 flex justify-end">
              <el-button type="primary" size="small" :disabled="!current.id" @click="openSubCreateDialog">添加过滤器</el-button>
            </div>
            <el-table :data="subRows" border>
              <el-table-column label="顺序" width="110">
                <template #default="{ $index }">
                  <el-button-group size="small">
                    <el-button :disabled="$index === 0" @click="moveSubUp($index)">↑</el-button>
                    <el-button :disabled="$index === subRows.length - 1" @click="moveSubDown($index)">↓</el-button>
                  </el-button-group>
                </template>
              </el-table-column>
              <el-table-column prop="filterName" label="过滤器" width="160">
                <template #default="{ row }"><el-tag>{{ row.filterName }}</el-tag></template>
              </el-table-column>
              <el-table-column label="参数" min-width="220">
                <template #default="{ row }"><span class="text-xs text-gray-600">{{ subArgsSummary(row) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" @click="openSubEditDialog(row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="removeSub(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="断言" name="predicate">
            <div class="mb-3 flex justify-end">
              <el-button type="primary" size="small" :disabled="!current.id" @click="openSubCreateDialog">添加断言</el-button>
            </div>
            <el-table :data="subRows" border>
              <el-table-column label="顺序" width="110">
                <template #default="{ $index }">
                  <el-button-group size="small">
                    <el-button :disabled="$index === 0" @click="moveSubUp($index)">↑</el-button>
                    <el-button :disabled="$index === subRows.length - 1" @click="moveSubDown($index)">↓</el-button>
                  </el-button-group>
                </template>
              </el-table-column>
              <el-table-column prop="predicateName" label="断言" width="160">
                <template #default="{ row }"><el-tag type="warning">{{ row.predicateName }}</el-tag></template>
              </el-table-column>
              <el-table-column label="参数" min-width="220">
                <template #default="{ row }"><span class="text-xs text-gray-600">{{ subArgsSummary(row) }}</span></template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" @click="openSubEditDialog(row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="removeSub(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="点击左侧路由查看/配置" />
    </el-card>

    <!-- 断言/过滤器编辑弹窗（v2 模板驱动：模板限制可填项，服务端校验） -->
    <el-dialog v-model="subEditOpen" :title="(tab === 'filter' ? '过滤器' : '断言') + (subEditingId ? '编辑' : '添加')" width="640">
      <el-form label-width="100">
        <el-form-item label="模板" required>
          <el-select v-model="subForm.name" filterable placeholder="选择模板" @change="onSubTemplateChange">
            <el-option
              v-for="t in subTemplates"
              :key="t.id"
              :label="tplLabel(t)"
              :value="t.name"
              :disabled="mountedSingletons.has(t.name)"
            />
          </el-select>
          <div v-if="mountedSingletons.size" class="text-xs text-gray-400 mt-1">
            已置灰项为「每路由一次」且已挂载的模板
          </div>
        </el-form-item>

        <!-- v2 模板 → 受限表单（list/map/嵌套 object 均可，含提示与校验正则） -->
        <el-form-item v-if="subUseV2" label="参数">
          <div class="w-full">
            <div v-if="subTree?.remark" class="text-xs text-gray-400 mb-2">{{ subTree.remark }}</div>
            <ValueTreeForm v-model="subForm.args" :slots="subSlots" />
            <div class="text-xs text-gray-400 mt-1">保存时按模板做服务端校验（必填/正则/枚举/类型）</div>
          </div>
        </el-form-item>

        <!-- 无 v2 模板（历史旧数据）→ 直接编辑 JSON 兜底 -->
        <el-form-item v-else label="参数(JSON)">
          <el-input v-model="legacyJson" type="textarea" :rows="6" placeholder="该模板无 v2 定义，直接填 JSON 参数（旧数据兼容）" />
        </el-form-item>

        <el-form-item label="顺序">
          <el-input-number v-model="subForm.sortNum" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subEditOpen = false">取消</el-button>
        <el-button type="primary" :loading="subSaving" @click="submitSub">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.route-list {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
.route-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.route-item:hover {
  border-color: #409eff;
  background: #f5f9ff;
}
.route-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
</style>
