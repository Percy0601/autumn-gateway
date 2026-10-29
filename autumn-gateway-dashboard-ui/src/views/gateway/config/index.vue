<script setup>
import { onMounted, ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayConfigApi, gatewayTemplateV2Api } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import ValueTreeForm from '../components/ValueTreeForm.vue'

const loading = ref(false)
const rows = ref([])
const profile = ref('dev')

async function loadData() {
  loading.value = true
  try {
    const body = await gatewayConfigApi.list(profile.value)
    rows.value = body.data || []
  } finally {
    loading.value = false
  }
}
onMounted(loadData)

// 分组展示顺序：与配置同步导出口径一致（SERVER→DISCOVERY→AUTHN→AUTHZ→LIMIT→SCG→CORS→CUSTOM）
const GROUP_ORDER = ['SERVER', 'DISCOVERY', 'AUTHN', 'AUTHZ', 'LIMIT', 'SCG', 'CORS', 'CUSTOM']
const GROUP_LABEL = {
  SERVER: '服务基础', DISCOVERY: '注册发现（Consul）', AUTHN: '认证（OAuth2）', AUTHZ: '鉴权（JWT）',
  LIMIT: '限流', SCG: '网关引擎（SCG）', CORS: '跨域 CORS', CUSTOM: '自定义',
}
const groupLabel = (g) => GROUP_LABEL[g] || g

// 按组折叠展示（组按业务顺序，组内按后端返回的 sort_num）
const groups = ref([])
function rebuildGroups() {
  const map = {}
  rows.value.forEach((r) => {
    const g = r.configGroup || 'CUSTOM'
    if (!map[g]) map[g] = []
    map[g].push(r)
  })
  const ordered = Object.keys(map).sort((a, b) => {
    const ia = GROUP_ORDER.indexOf(a); const ib = GROUP_ORDER.indexOf(b)
    return (ia < 0 ? GROUP_ORDER.length : ia) - (ib < 0 ? GROUP_ORDER.length : ib)
  })
  groups.value = ordered.map((g) => ({ group: g, items: map[g] }))
}
watch(rows, rebuildGroups, { deep: true })

const drawerOpen = ref(false)
const saving = ref(false)
const currentId = ref(null)
const form = reactive({
  profileCode: 'dev',
  configGroup: 'CUSTOM',
  configKey: '',
  configValue: '',
  valueType: 'STRING',
  isRequired: 0,
  isEncrypted: 0,
  sortNum: 0,
  remark: '',
})

function openCreate() {
  currentId.value = null
  Object.assign(form, { profileCode: profile.value, configGroup: 'CUSTOM', configKey: '', configValue: '', valueType: 'STRING', isRequired: 0, isEncrypted: 0, sortNum: 0, remark: '' })
  drawerOpen.value = true
}

function openEdit(row) {
  currentId.value = row.id
  Object.assign(form, {
    profileCode: row.profileCode,
    configGroup: row.configGroup,
    configKey: row.configKey,
    configValue: row.configValue || '',
    valueType: row.valueType || 'STRING',
    isRequired: row.isRequired || 0,
    isEncrypted: row.isEncrypted || 0,
    sortNum: row.sortNum || 0,
    remark: row.remark || '',
  })
  drawerOpen.value = true
}

async function save() {
  saving.value = true
  try {
    if (currentId.value) await gatewayConfigApi.update(currentId.value, form)
    else await gatewayConfigApi.create(form)
    ElMessage.success('已保存')
    drawerOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  const ok = await isConfirmed(`删除配置项 ${row.configKey}？`)
  if (!ok) return
  await gatewayConfigApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ==================== 从 CONFIG 模板添加 ====================
const tplDialogOpen = ref(false)
const tplLoading = ref(false)
const tplOptions = ref([])
const tplId = ref(null)
const tplTree = ref(null)
const tplValues = reactive({})
const tplSubmitting = ref(false)

async function openTplCreate() {
  tplDialogOpen.value = true
  tplLoading.value = true
  try {
    const body = await gatewayTemplateV2Api.templates('CONFIG')
    tplOptions.value = body.data || []
    tplId.value = tplOptions.value[0]?.id ?? null
    if (tplId.value) await loadTplTree()
  } finally {
    tplLoading.value = false
  }
}

async function loadTplTree() {
  tplTree.value = null
  Object.keys(tplValues).forEach((k) => delete tplValues[k])
  if (!tplId.value) return
  const body = await gatewayTemplateV2Api.templateTree(tplId.value)
  tplTree.value = body.data || null
}

async function submitTpl() {
  if (!tplId.value || !tplTree.value) return
  tplSubmitting.value = true
  try {
    const body = await gatewayConfigApi.createFromTemplate({
      profileCode: profile.value,
      tplId: tplId.value,
      values: { ...tplValues },
    })
    ElMessage.success(`已从模板添加 ${(body.data || []).length} 项配置`)
    tplDialogOpen.value = false
    loadData()
  } finally {
    tplSubmitting.value = false
  }
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <div class="mb-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-base font-bold">网关全局配置</span>
            <el-select v-model="profile" size="small" style="width:120px" @change="loadData">
              <el-option label="dev" value="dev" />
              <el-option label="test" value="test" />
              <el-option label="prod" value="prod" />
            </el-select>
          </div>
          <div class="flex gap-2">
            <el-button type="primary" size="small" @click="openTplCreate">从模板添加</el-button>
            <el-button size="small" @click="openCreate">手填配置项</el-button>
          </div>
        </div>
        <div class="text-xs text-gray-400 mt-2">
          按业务组展示：服务基础 → 注册发现 → 认证 → 鉴权 → 限流 → 网关引擎 → 跨域 → 自定义；
          「从模板添加」由 CONFIG 模板表单生成 key/value，模板可自定义不发版；sensitive 项自动加密存储。
          SCG/CORS 旧前缀 spring.cloud.gateway.* 在「配置同步」导出时自动归一化为 spring.cloud.gateway.server.webflux.*；
          CORS 组旧 KV 在结构化实例 globalcors 启用时由导出自动跳过（建议后续删除，统一走 STRUCT）。
        </div>
      </div>

      <div v-loading="loading" class="mt-4">
        <el-collapse v-for="g in groups" :key="g.group" class="mb-3">
          <el-collapse-item :title="`${groupLabel(g.group)}（${g.group}）· ${g.items.length} 项`" :name="g.group">
            <el-table :data="g.items" border>
              <el-table-column prop="configKey" label="配置 Key" min-width="260" />
              <el-table-column label="值" min-width="200">
                <template #default="{ row }">
                  <span v-if="row.isEncrypted === 1">******</span>
                  <span v-else>{{ row.configValue }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="valueType" label="类型" width="90" />
              <el-table-column label="必填" width="60">
                <template #default="{ row }">
                  <el-tag v-if="row.isRequired === 1" type="danger" size="small">必填</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button size="small" @click="openEdit(row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="remove(row)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-card>

    <el-drawer v-model="drawerOpen" :title="currentId ? '编辑配置项' : '新增配置项'" size="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="分组">
          <el-select v-model="form.configGroup">
            <el-option v-for="g in GROUP_ORDER" :key="g" :label="`${groupLabel(g)}（${g}）`" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="Key" required>
          <el-input v-model="form.configKey" placeholder="如 autumn.authz.jwt.secret" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="form.configValue" :type="form.isEncrypted ? 'password' : 'textarea'" :rows="3" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="form.valueType">
            <el-option v-for="t in ['STRING','INT','LONG','BOOL','DURATION','LIST','JSON','TEXTAREA']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch v-model="form.isRequired" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="加密存储">
          <el-switch v-model="form.isEncrypted" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="tplDialogOpen" title="从模板添加全局配置" width="720px">
      <div v-loading="tplLoading">
        <el-form label-width="90px">
          <el-form-item label="配置组模板">
            <el-select v-model="tplId" style="width:100%" @change="loadTplTree">
              <el-option v-for="t in tplOptions" :key="t.id" :value="t.id" :label="`${t.displayName}（${t.name}）`" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert v-if="tplTree" type="info" :closable="false" class="mb-2"
          :title="`生成规则：key = ${tplTree.name}.${tplTree.fields?.[0]?.exportKey || '…'}，填完值提交后按模板生成配置项（敏感项自动加密）`" />
        <ValueTreeForm v-if="tplTree" v-model="tplValues" :slots="tplTree.fields || []" />
        <el-empty v-else-if="!tplLoading" description="无可用 CONFIG 模板，请到模板管理创建" />
      </div>
      <template #footer>
        <el-button @click="tplDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="tplSubmitting" :disabled="!tplId || !tplTree" @click="submitTpl">生成配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
