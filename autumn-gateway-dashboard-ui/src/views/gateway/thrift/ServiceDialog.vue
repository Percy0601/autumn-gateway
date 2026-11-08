<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑 Thrift 服务' : '登记 Thrift 服务'"
    width="860px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="服务编码必须等于 IDL 里的 service 名"
      description="网关启动时会按这个名字去 IDL 目录里找服务，对不上会直接启动失败。填内联 IDL 后页面会自动帮你核对。"
      style="margin-bottom: 12px"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="small">
      <el-tabs v-model="tab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form-item label="服务编码" prop="serviceCode">
            <div class="row">
              <el-input v-model="form.serviceCode" placeholder="必须等于 IDL 里的 service 名，如 SomeService" />
              <el-tooltip content="把 IDL 里解析出的 service 名填进来" placement="top">
                <el-button :disabled="!parsedServiceNames.length" @click="applyServiceName">采用 IDL 名</el-button>
              </el-tooltip>
            </div>
          </el-form-item>
          <el-form-item label="展示名">
            <el-input v-model="form.serviceName" placeholder="中文描述，仅页面展示，不参与导出" />
          </el-form-item>
          <el-form-item label="目标地址" prop="host">
            <div class="row">
              <el-input v-model="form.host" placeholder="host，如 127.0.0.1" style="flex: 1" />
              <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 160px" />
            </div>
            <div class="hint">被测服务 autumn-service-framework 的 Thrift 端口默认 30880；这里填的是 Thrift 端口，不是 HTTP 端口。</div>
          </el-form-item>
          <el-form-item label="传输">
            <div class="row">
              <el-checkbox v-model="framedFlag">framed（TFramedTransport）</el-checkbox>
              <el-checkbox v-model="multiplexedFlag">multiplexed（TMultiplexedProtocol）</el-checkbox>
              <el-input
                v-model="form.multiplexedName"
                :disabled="form.multiplexed !== 1"
                placeholder="multiplexed 服务名，留空回退 IDL service 名"
                style="width: 300px"
              />
            </div>
            <div class="hint">
              序列化固定为 TBinaryProtocol，暂不支持 compact / JSON。被测服务 autumn-service-framework 用
              TMultiplexedProcessor 按<b>接口全限定名</b>注册，联调它时要勾 multiplexed 并填
              <code>xyz.wewin.autumn.sample.api.SomeService$Iface</code>（带 $Iface）；
              准确值看 provider 启动日志里的 <code>autumn provider export service:</code>。
            </div>
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="serviceEnabled" active-text="启用（参与导出）" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="选填" />
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane label="IDL 与解析结果" name="idl">
          <el-form-item label="IDL 来源" prop="idlContent">
            <el-radio-group v-model="idlSource">
              <el-radio-button value="inline">内联 IDL（推荐）</el-radio-button>
              <el-radio-button value="location">外部文件位置</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="idlSource === 'inline'" label="内联 IDL">
            <div class="idl-editor">
              <div class="idl-toolbar">
                <el-select
                  v-model="selectedTemplate"
                  placeholder="插入示例 IDL"
                  style="width: 280px"
                  @change="insertTemplate"
                >
                  <el-option v-for="t in IDL_TEMPLATES" :key="t.label" :label="t.label" :value="t.value" />
                </el-select>
                <span class="hint" style="margin: 0">支持 struct / enum / exception / service，不支持 include、typedef、service extends</span>
              </div>
              <el-input
                v-model="form.idlContent"
                type="textarea"
                :rows="11"
                spellcheck="false"
                placeholder="service DemoService { string echo(1: string msg) }"
              />
            </div>
          </el-form-item>
          <el-form-item v-else label="IDL 位置">
            <el-input v-model="form.idlLocation" placeholder="classpath:/idl/demo.thrift 或 file:/opt/idl/demo.thrift" />
            <div class="hint">填外部位置时页面无法在线核对 service 名，请自行确保与「服务编码」一致，且网关进程能读到该文件。</div>
          </el-form-item>

          <el-form-item label="解析结果">
            <div class="parse-box">
              <template v-if="idlSource === 'location'">
                <el-alert type="warning" :closable="false" show-icon title="使用外部 IDL 文件，无法在页面内解析方法与参数" description="转换规则里的方法名、参数名将需要手工填写。" />
              </template>
              <template v-else-if="!form.idlContent.trim()">
                <el-empty description="填入内联 IDL 后，这里会列出 service 名、方法与参数" :image-size="60" />
              </template>
              <template v-else>
                <div class="parse-head">
                  <span>解析到 {{ parsed.services.length }} 个 service</span>
                  <span v-if="codeCheck.state === 'ok'" class="ok">✓ 服务编码与 IDL 一致</span>
                  <span v-else-if="codeCheck.state === 'mismatch'" class="bad">✗ {{ codeCheck.message }}</span>
                  <span v-else class="bad">✗ {{ codeCheck.message }}</span>
                </div>
                <div v-for="w in parsed.warnings" :key="w" class="warn-line">⚠ {{ w }}</div>
                <div v-for="svc in parsed.services" :key="svc.name" class="svc-block">
                  <div class="svc-name">
                    <el-tag size="small" :type="svc.name === form.serviceCode ? 'success' : 'info'">service {{ svc.name }}</el-tag>
                    <span class="hint">{{ svc.methods.length }} 个方法</span>
                  </div>
                  <el-table :data="svc.methods" size="small" border>
                    <el-table-column prop="name" label="方法" width="150" />
                    <el-table-column label="入参（IDL 参数名将用于规则配置）" min-width="260">
                      <template #default="{ row }">
                        <template v-if="row.params.length">
                          <el-tag v-for="p in row.params" :key="p.id" size="small" class="param-tag">
                            {{ p.id }}: {{ p.required ? 'required ' : '' }}{{ p.type }} {{ p.name }}
                          </el-tag>
                        </template>
                        <span v-else class="hint">无参</span>
                      </template>
                    </el-table-column>
                    <el-table-column prop="returnType" label="返回" width="130" />
                    <el-table-column label="标记" width="90">
                      <template #default="{ row }">{{ row.oneway ? 'oneway' : '—' }}</template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </div>
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存修改' : '登记服务' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayThriftApi } from '@/api/gateway'
import { DEFAULT_SERVICE_FORM, IDL_TEMPLATES } from './constants'
import { checkServiceCode, parseIdl } from './idl'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null },
  services: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const isEdit = computed(() => !!props.row?.id)
const formRef = ref()
const saving = ref(false)
const tab = ref('basic')
const idlSource = ref('inline')
const selectedTemplate = ref('')
const form = reactive(DEFAULT_SERVICE_FORM())

const parsed = computed(() => (idlSource.value === 'inline' ? parseIdl(form.idlContent) : { services: [], warnings: [] }))
const parsedServiceNames = computed(() => parsed.value.services.map((s) => s.name))
const codeCheck = computed(() => checkServiceCode(form.idlContent, form.serviceCode))

const framedFlag = computed({
  get: () => form.framed === 1,
  set: (v) => { form.framed = v ? 1 : 0 },
})
const multiplexedFlag = computed({
  get: () => form.multiplexed === 1,
  set: (v) => { form.multiplexed = v ? 1 : 0 },
})
const serviceEnabled = computed({
  get: () => form.status === 1,
  set: (v) => { form.status = v ? 1 : 0 },
})

const rules = {
  serviceCode: [{ validator: validateServiceCode, trigger: 'blur' }],
  host: [{ required: true, message: '请填写 Thrift 服务 host', trigger: 'blur' }],
  idlContent: [{ validator: validateIdl, trigger: 'change' }],
}

function validateServiceCode(_rule, value, callback) {
  if (!value || !value.trim()) return callback(new Error('请填写服务编码'))
  const dup = props.services.some((s) => s.serviceCode === value.trim() && s.id !== props.row?.id)
  if (dup) return callback(new Error('服务编码已存在，不能重复'))
  if (idlSource.value === 'inline' && form.idlContent.trim()) {
    const c = checkServiceCode(form.idlContent, value.trim())
    if (c.state === 'mismatch' || c.state === 'unknown') return callback(new Error(c.message))
  }
  callback()
}

function validateIdl(_rule, _value, callback) {
  const ok = idlSource.value === 'inline' ? !!form.idlContent.trim() : !!form.idlLocation.trim()
  callback(ok ? undefined : new Error('内联 IDL 与 IDL 位置至少填一个'))
}

function applyServiceName() {
  if (parsedServiceNames.value.length) {
    form.serviceCode = parsedServiceNames.value[0]
    formRef.value?.clearValidate(['serviceCode'])
  }
}

function insertTemplate(value) {
  form.idlContent = value
  selectedTemplate.value = ''
  if (!form.serviceCode) applyServiceName()
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const row = props.row || {}
    Object.assign(form, DEFAULT_SERVICE_FORM(), {
      serviceCode: row.serviceCode || '',
      serviceName: row.serviceName || '',
      host: row.host || '127.0.0.1',
      port: row.port || 19090,
      idlLocation: row.idlLocation || '',
      idlContent: row.idlContent || '',
      multiplexedName: row.multiplexedName || '',
      framed: row.framed == null ? 1 : row.framed,
      multiplexed: row.multiplexed == null ? 0 : row.multiplexed,
      status: row.status == null ? 1 : row.status,
      remark: row.remark || '',
    })
    idlSource.value = row.idlContent ? 'inline' : row.idlLocation ? 'location' : 'inline'
    tab.value = 'basic'
    formRef.value?.clearValidate()
  },
  { immediate: true },
)

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    const idlMissing = idlSource.value === 'inline' ? !form.idlContent.trim() : !form.idlLocation.trim()
    const codeMismatch = idlSource.value === 'inline' && ['mismatch', 'unknown'].includes(codeCheck.value.state)
    tab.value = idlMissing || codeMismatch ? 'idl' : 'basic'
    ElMessage.warning('请先修正表单中标红的内容')
    return
  }
  const payload = {
    serviceCode: form.serviceCode.trim(),
    serviceName: form.serviceName.trim(),
    host: form.host.trim(),
    port: Number(form.port) || 0,
    idlLocation: idlSource.value === 'location' ? form.idlLocation.trim() : '',
    idlContent: idlSource.value === 'inline' ? form.idlContent : '',
    multiplexedName: form.multiplexed === 1 ? form.multiplexedName.trim() : '',
    framed: form.framed,
    multiplexed: form.multiplexed,
    status: form.status,
    remark: form.remark,
  }
  saving.value = true
  try {
    if (isEdit.value) await gatewayThriftApi.updateService(props.row.id, payload)
    else await gatewayThriftApi.createService(payload)
    ElMessage.success(isEdit.value ? '服务已更新' : '服务已登记')
    visible.value = false
    emit('saved')
  } catch {
    // 拦截器已经统一提示后端错误，这里不再重复弹窗
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.row { display: flex; align-items: center; gap: 8px; width: 100%; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; line-height: 1.6; margin-top: 2px; }
.idl-editor { width: 100%; }
.idl-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.idl-editor :deep(textarea) { font-family: Consolas, Menlo, Monaco, monospace; font-size: 12px; line-height: 1.6; }
.parse-box { width: 100%; min-height: 60px; }
.parse-head { display: flex; align-items: center; gap: 16px; font-size: 13px; margin-bottom: 8px; flex-wrap: wrap; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.warn-line { color: #e6a23c; font-size: 12px; line-height: 1.8; }
.svc-block { margin-top: 12px; }
.svc-name { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.param-tag { margin: 2px 4px 2px 0; }
</style>
