<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayRouteApi, gatewayCenterApi, gatewayTemplateApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'

const loading = ref(false)
const rows = ref([])
const centers = ref([])
const filterTemplates = ref([])
const predicateTemplates = ref([])

async function loadData() {
  loading.value = true
  try {
    const body = await gatewayRouteApi.list()
    rows.value = body.data || []
  } finally { loading.value = false }
}
async function loadDicts() {
  const [c, f, p] = await Promise.all([
    gatewayCenterApi.list(),
    gatewayTemplateApi.filterList(),
    gatewayTemplateApi.predicateList(),
  ])
  centers.value = c.data || []
  filterTemplates.value = (f.data || []).map(t => ({ ...t, schema: safeParse(t.argsSchemaJson) }))
  predicateTemplates.value = (p.data || []).map(t => ({ ...t, schema: safeParse(t.argsSchemaJson) }))
}
function safeParse(s) { try { return JSON.parse(s || '[]') } catch { return [] } }
onMounted(() => { loadData(); loadDicts() })

// ---------- 路由表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentId = ref(null)
const form = reactive({
  routeCode: '', routeName: '', centerId: null, namespace: '',
  serviceName: '', uri: '', orderNum: 0, remark: '',
})
const serviceOptions = ref([])
const loadingServices = ref(false)

async function onCenterChange() {
  serviceOptions.value = []
  if (!form.centerId) return
  loadingServices.value = true
  try {
    const r = await gatewayCenterApi.services(form.centerId)
    serviceOptions.value = r.data || []
  } catch (e) {
    ElMessage.warning('拉取服务列表失败：' + (e?.response?.data?.message || e.message))
  } finally { loadingServices.value = false }
}
function onServiceChange() {
  if (form.serviceName) form.uri = 'lb://' + form.serviceName
}

function openCreate() {
  currentId.value = null
  Object.assign(form, { routeCode: '', routeName: '', centerId: null, namespace: '', serviceName: '', uri: '', orderNum: 0, remark: '' })
  serviceOptions.value = []
  drawerOpen.value = true
}
function openEdit(row) {
  currentId.value = row.id
  Object.assign(form, {
    routeCode: row.routeCode, routeName: row.routeName,
    centerId: row.centerId, namespace: row.namespace || '',
    serviceName: row.serviceName || '', uri: row.uri || '',
    orderNum: row.orderNum ?? 0, remark: row.remark || '',
  })
  serviceOptions.value = []
  if (row.centerId) onCenterChange()
  drawerOpen.value = true
}
async function save() {
  if (!form.routeCode || !form.routeName) return ElMessage.warning('路由编码/名称必填')
  if (!form.uri) return ElMessage.warning('URI 必填（选服务自动填 lb://，或手填 http://...）')
  saving.value = true
  try {
    const payload = { ...form, orderNum: Number(form.orderNum) || 0 }
    if (currentId.value) await gatewayRouteApi.update(currentId.value, payload)
    else await gatewayRouteApi.create(payload)
    ElMessage.success('已保存')
    drawerOpen.value = false
    loadData()
  } finally { saving.value = false }
}
async function remove(row) {
  if (!await isConfirmed(`删除路由 ${row.routeCode}？`)) return
  await gatewayRouteApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ---------- 断言/过滤器抽屉 ----------
const subOpen = ref(false)
const subTab = ref('filter') // filter | predicate
const subTitle = ref('')
const subRows = ref([])
const subEditOpen = ref(false)
const subForm = reactive({ name: '', sortNum: 0, args: {} })
const subSchema = computed(() => {
  const list = subTab.value === 'filter' ? filterTemplates.value : predicateTemplates.value
  const t = list.find(x => x.filterName === subForm.name || x.predicateName === subForm.name)
  return t?.schema || []
})
const subSelectedTpl = computed(() => {
  const list = subTab.value === 'filter' ? filterTemplates.value : predicateTemplates.value
  return list.find(x => x.filterName === subForm.name || x.predicateName === subForm.name)
})

async function openSub(row, tab) {
  subTab.value = tab
  subTitle.value = `${row.routeCode} —— ${tab === 'filter' ? '过滤器' : '断言'}`
  currentId.value = row.id
  subOpen.value = true
  await loadSub()
}
async function loadSub() {
  const api = subTab.value === 'filter' ? gatewayRouteApi.filters : gatewayRouteApi.predicates
  const r = await api(currentId.value)
  subRows.value = (r.data || []).map(x => ({ ...x, args: safeParse(x.argsJson) }))
}
function openSubCreate() {
  Object.assign(subForm, { name: '', sortNum: subRows.value.length + 1, args: {} })
  subEditOpen.value = true
}
function openSubEdit(row) {
  Object.assign(subForm, { name: row.filterName || row.predicateName, sortNum: row.sortNum, args: { ...row.args } })
  subEditOpen.value = true
}
function onSubTemplateChange() {
  // 用模板默认值初始化 args
  subForm.args = {}
  for (const f of subSchema.value) {
    if (f.default !== undefined) subForm.args[f.key] = f.default
  }
}
async function saveSub() {
  if (!subForm.name) return ElMessage.warning('请选择模板')
  // 必填校验
  for (const f of subSchema.value) {
    if (f.required && (subForm.args[f.key] === undefined || subForm.args[f.key] === '')) {
      return ElMessage.warning(`参数 ${f.label || f.key} 必填`)
    }
  }
  const payload = subTab.value === 'filter'
    ? { filterName: subForm.name, sortNum: Number(subForm.sortNum) || 0, argsJson: JSON.stringify(subForm.args) }
    : { predicateName: subForm.name, sortNum: Number(subForm.sortNum) || 0, argsJson: JSON.stringify(subForm.args) }
  await (subTab.value === 'filter' ? gatewayRouteApi.addFilter : gatewayRouteApi.addPredicate)(currentId.value, payload)
  ElMessage.success('已保存')
  subEditOpen.value = false
  loadSub()
}
async function removeSub(row) {
  if (!await isConfirmed('删除该条？')) return
  const api = subTab.value === 'filter' ? gatewayRouteApi.removeFilter : gatewayRouteApi.removePredicate
  await api(row.id)
  loadSub()
}

function tplOptions() {
  return subTab.value === 'filter' ? filterTemplates.value : predicateTemplates.value
}
function centerName(id) {
  return centers.value.find(c => c.id === id)?.centerName || id || '—'
}
</script>

<template>
  <div class="p-4">
    <el-card>
      <div class="mb-3 flex justify-between">
        <el-button type="primary" @click="openCreate">新增路由</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="routeCode" label="路由编码" width="160" />
        <el-table-column prop="routeName" label="名称" />
        <el-table-column label="配置中心/服务" width="220">
          <template #default="{ row }">
            <div v-if="row.centerId">{{ centerName(row.centerId) }} / {{ row.serviceName || '—' }}</div>
            <div v-else style="color:#999">手填直连</div>
          </template>
        </el-table-column>
        <el-table-column prop="uri" label="URI" />
        <el-table-column prop="orderNum" label="顺序" width="70" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="openSub(row, 'predicate')">断言</el-button>
            <el-button size="small" @click="openSub(row, 'filter')">过滤器</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 路由抽屉 -->
    <el-drawer v-model="drawerOpen" title="路由配置" size="520">
      <el-form label-width="100">
        <el-form-item label="路由编码" required><el-input v-model="form.routeCode" placeholder="如 route-order" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.routeName" /></el-form-item>
        <el-form-item label="配置中心">
          <el-select v-model="form.centerId" placeholder="选了才能从注册中心拉服务" clearable @change="onCenterChange">
            <el-option v-for="c in centers" :key="c.id" :label="c.centerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.centerId" label="命名空间">
          <el-input v-model="form.namespace" placeholder="Nacos 用；Consul 留空" />
        </el-form-item>
        <el-form-item v-if="form.centerId" label="已注册服务">
          <el-select v-model="form.serviceName" filterable clearable
                     :loading="loadingServices" placeholder="从注册中心选；没有就手填 URI"
                     @change="onServiceChange">
            <el-option v-for="s in serviceOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="URI" required>
          <el-input v-model="form.uri" placeholder="选服务后自动填 lb://service，也可手填 http://host:port" />
        </el-form-item>
        <el-form-item label="顺序"><el-input-number v-model="form.orderNum" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 断言/过滤器抽屉 -->
    <el-drawer v-model="subOpen" :title="subTitle" size="560">
      <template #header>
        <div class="flex items-center justify-between">
          <span>{{ subTitle }}</span>
          <el-button size="small" type="primary" @click="openSubCreate">添加</el-button>
        </div>
      </template>
      <el-table :data="subRows" border size="small">
        <el-table-column prop="filterName" label="过滤器" width="160">
          <template #default="{ row }">{{ row.filterName || row.predicateName }}</template>
        </el-table-column>
        <el-table-column label="参数">
          <template #default="{ row }">
            <code>{{ row.argsJson }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="sortNum" label="顺序" width="70" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="openSubEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeSub(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 子项编辑弹窗：模板驱动表单 -->
    <el-dialog v-model="subEditOpen" :title="subTab === 'filter' ? '过滤器参数' : '断言参数'" width="480">
      <el-form label-width="100">
        <el-form-item label="模板" required>
          <el-select v-model="subForm.name" placeholder="选模板" @change="onSubTemplateChange">
            <el-option v-for="t in tplOptions()" :key="t.id"
                       :label="(t.displayName || t.filterName || t.predicateName) + ' (' + (t.filterName || t.predicateName) + ')'"
                       :value="t.filterName || t.predicateName" />
          </el-select>
        </el-form-item>
        <template v-for="f in subSchema" :key="f.key">
          <el-form-item :label="f.label || f.key" :required="!!f.required">
            <el-input v-if="f.type === 'string' || !f.type" v-model="subForm.args[f.key]" :placeholder="f.placeholder || ''" />
            <el-input-number v-else-if="f.type === 'int' || f.type === 'long'" v-model="subForm.args[f.key]" />
            <el-select v-else-if="f.type === 'enum'" v-model="subForm.args[f.key]">
              <el-option v-for="o in (f.options || [])" :key="o" :label="o" :value="o" />
            </el-select>
            <el-switch v-else-if="f.type === 'bool'" v-model="subForm.args[f.key]" />
          </el-form-item>
        </template>
        <el-form-item label="顺序"><el-input-number v-model="subForm.sortNum" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subEditOpen = false">取消</el-button>
        <el-button type="primary" @click="saveSub">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
