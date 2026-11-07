<template>
  <div class="page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="协议转换：HTTP endpoint → Thrift 泛化调用"
      description="header / queryString / requestBody / path 变量 / 常量按规则映射到 thrift 参数，响应形态也可配。路由上写 ThriftGeneric=<service>,<method>,<conversion_code> 引用这里的规则。"
      style="margin-bottom: 12px"
    />

    <el-card shadow="never" style="margin-bottom: 14px">
      <template #header>
        <div class="hd">
          <span>Thrift 服务（IDL 元数据）</span>
          <div>
            <el-button size="small" @click="openPreview">导出预览</el-button>
            <el-button size="small" type="primary" @click="openService()">新增服务</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="services" border size="small">
        <el-table-column prop="serviceCode" label="服务编码（=IDL service 名）" min-width="190" />
        <el-table-column prop="serviceName" label="展示名" width="130" />
        <el-table-column label="地址" width="160">
          <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
        </el-table-column>
        <el-table-column label="IDL 来源" min-width="180">
          <template #default="{ row }">{{ row.idlLocation || (row.idlContent ? '内联 IDL' : '') }}</template>
        </el-table-column>
        <el-table-column label="传输" width="170">
          <template #default="{ row }">
            framed={{ row.framed === 1 ? 'Y' : 'N' }} · mux={{ row.multiplexed === 1 ? 'Y' : 'N' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openService(row)">编辑</el-button>
            <el-button link type="danger" @click="removeService(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="hd">
          <span>转换规则（HTTP 入参/出参映射）</span>
          <el-button size="small" type="primary" @click="openConversion()">新增规则</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="conversions" border size="small">
        <el-table-column prop="conversionCode" label="规则名（路由第三参）" min-width="170" />
        <el-table-column prop="conversionName" label="展示名" width="130" />
        <el-table-column prop="serviceCode" label="Thrift 服务" width="160" />
        <el-table-column prop="methodName" label="方法" width="120" />
        <el-table-column prop="httpPath" label="HTTP 路径（参考）" min-width="160" show-overflow-tooltip />
        <el-table-column label="入参" width="150">
          <template #default="{ row }">
            {{ row.includeBody === 1 ? '请求体打底' : '仅规则取值' }} · {{ argCount(row) }} 条
          </template>
        </el-table-column>
        <el-table-column label="响应" width="110">
          <template #default="{ row }">{{ row.responseMode || 'ENVELOPE' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConversion(row)">编辑</el-button>
            <el-button link type="danger" @click="removeConversion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 服务编辑 -->
    <el-dialog v-model="serviceDlg" :title="editingServiceId ? '编辑 Thrift 服务' : '新增 Thrift 服务'" width="720px">
      <el-form :model="serviceForm" label-width="130px" size="small">
        <el-form-item label="服务编码" required>
          <el-input v-model="serviceForm.serviceCode" placeholder="必须等于 IDL 里的 service 名，如 DemoService" />
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="serviceForm.serviceName" placeholder="中文描述，仅页面展示" />
        </el-form-item>
        <el-form-item label="host / port" required>
          <div style="display: flex; gap: 8px">
            <el-input v-model="serviceForm.host" placeholder="127.0.0.1" />
            <el-input-number v-model="serviceForm.port" :min="1" :max="65535" controls-position="right" style="width: 140px" />
          </div>
        </el-form-item>
        <el-form-item label="IDL 位置">
          <el-input v-model="serviceForm.idlLocation" placeholder="classpath:/idl/demo.thrift 或 file:/opt/idl/demo.thrift" />
        </el-form-item>
        <el-form-item label="内联 IDL">
          <el-input v-model="serviceForm.idlContent" type="textarea" :rows="8" placeholder="service DemoService { string echo(1: string name) }" />
        </el-form-item>
        <el-form-item label="传输">
          <el-checkbox v-model="framedFlag">framed</el-checkbox>
          <el-checkbox v-model="multiplexedFlag">multiplexed</el-checkbox>
          <el-input v-model="serviceForm.multiplexedName" placeholder="multiplexed 服务名" style="width: 200px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="serviceEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="serviceForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveService">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转换规则编辑 -->
    <el-dialog v-model="conversionDlg" :title="editingConversionId ? '编辑转换规则' : '新增转换规则'" width="900px">
      <el-form :model="conversionForm" label-width="130px" size="small">
        <el-form-item label="规则名" required>
          <el-input v-model="conversionForm.conversionCode" placeholder="路由过滤器第三参引用它，如 demoEcho" />
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="conversionForm.conversionName" />
        </el-form-item>
        <el-form-item label="Thrift 服务 / 方法">
          <div style="display: flex; gap: 8px">
            <el-select v-model="conversionForm.serviceCode" placeholder="选择服务" style="width: 260px">
              <el-option v-for="s in services" :key="s.serviceCode" :label="s.serviceCode" :value="s.serviceCode" />
            </el-select>
            <el-input v-model="conversionForm.methodName" placeholder="方法名，留空则由路由/动态分发决定" />
          </div>
        </el-form-item>
        <el-form-item label="HTTP 路径（参考）">
          <el-input v-model="conversionForm.httpPath" placeholder="POST /thrift/DemoService/echo" />
        </el-form-item>
        <el-form-item label="入参模式">
          <el-checkbox v-model="includeBodyFlag">请求体 JSON 作为参数基座（include-body）</el-checkbox>
        </el-form-item>
        <el-form-item label="参数规则">
          <div style="width: 100%">
            <el-table :data="argRules" size="small" border>
              <el-table-column label="thrift 参数" width="140">
                <template #default="{ row }"><el-input v-model="row.arg" size="small" /></template>
              </el-table-column>
              <el-table-column label="来源" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.source" size="small">
                    <el-option label="BODY" value="BODY" />
                    <el-option label="QUERY" value="QUERY" />
                    <el-option label="HEADER" value="HEADER" />
                    <el-option label="PATH" value="PATH" />
                    <el-option label="FIXED" value="FIXED" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="取值 key" min-width="150">
                <template #default="{ row }"><el-input v-model="row.key" size="small" placeholder="BODY 字段 / query 名 / header 名 / 路径变量" /></template>
              </el-table-column>
              <el-table-column label="常量值" width="140">
                <template #default="{ row }"><el-input v-model="row.value" size="small" :disabled="row.source !== 'FIXED'" /></template>
              </el-table-column>
              <el-table-column label="必填" width="70">
                <template #default="{ row }"><el-checkbox v-model="row.required" /></template>
              </el-table-column>
              <el-table-column label="" width="60">
                <template #default="{ $index }"><el-button link type="danger" @click="argRules.splice($index, 1)">删</el-button></template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top: 8px" @click="addArgRule">+ 加一条</el-button>
          </div>
        </el-form-item>
        <el-form-item label="响应形态">
          <div style="display: flex; gap: 12px; align-items: center">
            <el-select v-model="conversionForm.responseMode" style="width: 180px">
              <el-option label="ENVELOPE（success/cost/data）" value="ENVELOPE" />
              <el-option label="DATA（只返回 data）" value="DATA" />
            </el-select>
            <el-input-number v-model="conversionForm.successStatus" :min="0" :max="599" controls-position="right" style="width: 150px" />
            <span style="color: #909399">成功状态码，0=默认 200</span>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="conversionForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="conversionDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConversion">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDlg" title="autumn.thrift.* 导出预览" width="860px">
      <el-input v-model="previewText" type="textarea" :rows="18" readonly />
      <el-alert
        v-if="!previewText"
        type="warning"
        :closable="false"
        title="还没有启用中的 thrift 服务，配置不会进入导出文本"
        style="margin-top: 8px"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayThriftApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'

const loading = ref(false)
const saving = ref(false)
const services = ref([])
const conversions = ref([])

const serviceDlg = ref(false)
const editingServiceId = ref(null)
const serviceForm = reactive({
  serviceCode: '', serviceName: '', host: '127.0.0.1', port: 19090,
  idlLocation: '', idlContent: '', multiplexedName: '', remark: '', status: 1, framed: 1, multiplexed: 0,
})
const framedFlag = computed({
  get: () => serviceForm.framed === 1,
  set: (v) => { serviceForm.framed = v ? 1 : 0 },
})
const multiplexedFlag = computed({
  get: () => serviceForm.multiplexed === 1,
  set: (v) => { serviceForm.multiplexed = v ? 1 : 0 },
})
const serviceEnabled = computed({
  get: () => serviceForm.status === 1,
  set: (v) => { serviceForm.status = v ? 1 : 0 },
})

const conversionDlg = ref(false)
const editingConversionId = ref(null)
const conversionForm = reactive({
  conversionCode: '', conversionName: '', serviceCode: '', methodName: '', httpPath: '',
  includeBody: 1, responseMode: 'ENVELOPE', successStatus: 0, remark: '', status: 1, argumentsJson: '',
})
const argRules = ref([])
const includeBodyFlag = computed({
  get: () => conversionForm.includeBody === 1,
  set: (v) => { conversionForm.includeBody = v ? 1 : 0 },
})

const previewDlg = ref(false)
const previewText = ref('')

async function load() {
  loading.value = true
  try {
    const [s, c] = await Promise.all([gatewayThriftApi.services(), gatewayThriftApi.conversions()])
    services.value = s.data || []
    conversions.value = c.data || []
  } finally {
    loading.value = false
  }
}
onMounted(load)

function argCount(row) {
  try {
    const list = JSON.parse(row.argumentsJson || '[]')
    return Array.isArray(list) ? list.length : 0
  } catch { return 0 }
}

function openService(row) {
  editingServiceId.value = row ? row.id : null
  Object.assign(serviceForm, {
    serviceCode: '', serviceName: '', host: '127.0.0.1', port: 19090,
    idlLocation: '', idlContent: '', multiplexedName: '', remark: '', status: 1, framed: 1, multiplexed: 0,
    ...(row || {}),
  })
  serviceDlg.value = true
}

async function saveService() {
  if (!serviceForm.serviceCode) return ElMessage.warning('服务编码必填')
  if (!serviceForm.idlContent && !serviceForm.idlLocation) return ElMessage.warning('IDL 位置和内联 IDL 至少填一个')
  saving.value = true
  try {
    if (editingServiceId.value) await gatewayThriftApi.updateService(editingServiceId.value, serviceForm)
    else await gatewayThriftApi.createService(serviceForm)
    ElMessage.success('已保存')
    serviceDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message)
  } finally { saving.value = false }
}

async function removeService(row) {
  if (!await isConfirmed(`删除 thrift 服务 ${row.serviceCode}？引用它的转换规则会一起失效`)) return
  await gatewayThriftApi.removeService(row.id)
  load()
}

function addArgRule() {
  argRules.value.push({ arg: '', source: 'BODY', key: '', value: '', required: false, defaultValue: '' })
}

function openConversion(row) {
  editingConversionId.value = row ? row.id : null
  Object.assign(conversionForm, {
    conversionCode: '', conversionName: '', serviceCode: '', methodName: '', httpPath: '',
    includeBody: 1, responseMode: 'ENVELOPE', successStatus: 0, remark: '', status: 1, argumentsJson: '',
    ...(row || {}),
  })
  try {
    const parsed = JSON.parse(conversionForm.argumentsJson || '[]')
    argRules.value = Array.isArray(parsed)
      ? parsed.map((r) => ({ arg: r.arg || '', source: r.source || 'BODY', key: r.key || '', value: r.value || '', required: !!r.required, defaultValue: r.defaultValue || '' }))
      : []
  } catch { argRules.value = [] }
  conversionDlg.value = true
}

async function saveConversion() {
  if (!conversionForm.conversionCode) return ElMessage.warning('规则名必填')
  if (!conversionForm.serviceCode) return ElMessage.warning('请选择 thrift 服务')
  const rules = argRules.value.filter((r) => r.arg)
  conversionForm.argumentsJson = JSON.stringify(rules)
  saving.value = true
  try {
    if (editingConversionId.value) await gatewayThriftApi.updateConversion(editingConversionId.value, conversionForm)
    else await gatewayThriftApi.createConversion(conversionForm)
    ElMessage.success('已保存')
    conversionDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e.message)
  } finally { saving.value = false }
}

async function removeConversion(row) {
  if (!await isConfirmed(`删除转换规则 ${row.conversionCode}？`)) return
  await gatewayThriftApi.removeConversion(row.id)
  load()
}

async function openPreview() {
  const res = await gatewayThriftApi.preview()
  previewText.value = res.data?.text || ''
  previewDlg.value = true
}
</script>

<style scoped>
.page { padding: 4px; }
.hd { display: flex; align-items: center; justify-content: space-between; }
</style>
