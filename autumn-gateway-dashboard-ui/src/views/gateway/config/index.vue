<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayConfigApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'

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

// 按组折叠展示：前端分组，不改数据
const groups = ref([])
function rebuildGroups() {
  const map = {}
  rows.value.forEach((r) => {
    if (!map[r.configGroup]) map[r.configGroup] = []
    map[r.configGroup].push(r)
  })
  groups.value = Object.entries(map).map(([g, items]) => ({ group: g, items }))
}
import { watch } from 'vue'
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
          <el-button type="primary" size="small" @click="openCreate">新增配置项</el-button>
        </div>
        <div class="text-xs text-gray-400 mt-2">
          配置项按组折叠展示；SCG/CORS 组里旧前缀 spring.cloud.gateway.* 在「配置同步」导出时会自动归一化为 spring.cloud.gateway.server.webflux.*（SCG 5.0.3 绑定前缀）
        </div>
      </div>

      <div v-loading="loading" class="mt-4">
        <el-collapse v-for="g in groups" :key="g.group" class="mb-3">
          <el-collapse-item :title="`${g.group}（${g.items.length}）`" :name="g.group">
            <el-table :data="g.items" border size="small">
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
            <el-option v-for="g in ['SERVER','DATASOURCE','SCG','AUTHN','AUTHZ','LIMIT','CORS','CUSTOM']" :key="g" :label="g" :value="g" />
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
  </div>
</template>
