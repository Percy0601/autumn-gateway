<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayCenterApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'

const rows = ref([])
const loading = ref(false)
const dlg = ref(false)
const saving = ref(false)
const currentId = ref(null)
const form = reactive({ centerCode: '', centerName: '', centerType: 'CONSUL', host: '127.0.0.1', port: 8500, aclToken: '', namespace: '', remark: '' })

async function load() {
  loading.value = true
  try { rows.value = (await gatewayCenterApi.list()).data || [] }
  finally { loading.value = false }
}
onMounted(load)

function openCreate() {
  currentId.value = null
  Object.assign(form, { centerCode: '', centerName: '', centerType: 'CONSUL', host: '127.0.0.1', port: 8500, aclToken: '', namespace: '', remark: '' })
  dlg.value = true
}
function openEdit(r) {
  currentId.value = r.id
  Object.assign(form, { ...r })
  dlg.value = true
}
async function save() {
  if (!form.centerCode || !form.centerName || !form.host) return ElMessage.warning('编码/名称/host 必填')
  saving.value = true
  try {
    if (currentId.value) await gatewayCenterApi.update(currentId.value, form)
    else await gatewayCenterApi.create(form)
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally { saving.value = false }
}
async function remove(r) {
  if (!await isConfirmed(`删除 ${r.centerName}？`)) return
  await gatewayCenterApi.remove(r.id)
  load()
}
async function testServices(r) {
  try {
    const s = await gatewayCenterApi.services(r.id)
    ElMessage.success('连通，已注册服务 ' + (s.data || []).length + ' 个')
  } catch (e) {
    ElMessage.error('连接失败：' + (e?.response?.data?.message || e.message))
  }
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <div class="mb-4">
        <div class="flex justify-between items-center">
          <span class="text-base font-bold">配置中心</span>
          <el-button type="primary" @click="openCreate">新增连接</el-button>
        </div>
        <div class="text-xs text-gray-400 mt-2">
          维护 Consul/Nacos 连接；加路由时从这里选，再拉注册中心的服务列表。「配置同步」可把聚合配置推送到这里（Consul KV / Nacos 配置）
        </div>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="centerCode" label="编码" width="160" />
        <el-table-column prop="centerName" label="名称" />
        <el-table-column prop="centerType" label="类型" width="90" />
        <el-table-column label="地址">
          <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
        </el-table-column>
        <el-table-column prop="namespace" label="命名空间" width="140" />
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button size="small" @click="testServices(row)">测试拉服务</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dlg" title="配置中心连接" width="520">
        <el-form label-width="100">
          <el-form-item label="编码"><el-input v-model="form.centerCode" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="form.centerName" /></el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.centerType">
              <el-option label="Consul" value="CONSUL" />
              <el-option label="Nacos（暂未支持）" value="NACOS" disabled />
            </el-select>
          </el-form-item>
          <el-form-item label="Host"><el-input v-model="form.host" /></el-form-item>
          <el-form-item label="Port"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item>
          <el-form-item label="ACL Token"><el-input v-model="form.aclToken" /></el-form-item>
          <el-form-item label="命名空间"><el-input v-model="form.namespace" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dlg = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
