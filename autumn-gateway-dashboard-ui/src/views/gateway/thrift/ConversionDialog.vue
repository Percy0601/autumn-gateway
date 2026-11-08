<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑转换规则' : '新增转换规则'"
    width="1060px"
    :close-on-click-modal="false"
    destroy-on-close
    top="6vh"
  >
    <div class="dlg-body">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="这里的「服务 / 方法 / HTTP 路径」只是标注，不参与配置导出"
        description="真正决定调用哪个 Thrift 服务与方法的是路由上的 ThriftGeneric 过滤器短写；本页导出的只有 mappings（参数映射 + 响应形态）。可在「预览与联调」页一键复制路由写法和 curl。"
        style="margin-bottom: 12px"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="small">
        <el-tabs v-model="tab">
          <!-- ------------------------------------------------ 基础 -->
          <el-tab-pane label="① 基础信息" name="basic">
            <el-form-item label="规则名" prop="conversionCode">
              <el-input v-model="form.conversionCode" placeholder="路由过滤器第三参引用它，如 demoEcho" />
              <div class="hint">导出为 autumn.thrift.mappings[].name；只能用一个不含逗号的名字。</div>
            </el-form-item>
            <el-form-item label="展示名">
              <el-input v-model="form.conversionName" placeholder="选填，仅页面展示" />
            </el-form-item>
            <el-form-item label="关联服务" prop="serviceCode">
              <el-select v-model="form.serviceCode" filterable placeholder="选择已登记的 Thrift 服务" style="width: 100%" @change="onServiceChange">
                <el-option
                  v-for="s in enabledServices"
                  :key="s.serviceCode"
                  :label="`${s.serviceCode}${s.serviceName ? ' · ' + s.serviceName : ''}（${s.host}:${s.port}）`"
                  :value="s.serviceCode"
                />
              </el-select>
              <div v-if="!enabledServices.length" class="hint bad-hint">还没有启用的 Thrift 服务，请先到上面「登记服务」。</div>
            </el-form-item>
            <el-form-item label="Thrift 方法">
              <el-select
                v-model="form.methodName"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="从 IDL 里选，或留空走动态分发"
                style="width: 100%"
                @change="onMethodChange"
              >
                <el-option v-for="m in methodOptions" :key="m.name" :label="`${m.name}（${m.params.length} 个参数）`" :value="m.name" />
              </el-select>
              <div class="hint">
                留空 = 由路由的路径变量 <code>{service}/{method}</code> 或请求头 <code>X-Thrift-Service</code> / <code>X-Thrift-Method</code> 决定；
                {{ methodOptions.length ? `已从所选服务的 IDL 解析出 ${methodOptions.length} 个方法。` : '所选服务没有内联 IDL，方法名需手工填写。' }}
              </div>
            </el-form-item>
            <el-form-item label="HTTP 路径（参考）">
              <el-input v-model="form.httpPath" placeholder="POST /thrift/SomeService/echo" />
              <div class="hint">仅用于生成下面的 curl 示例与路由 Path 建议，不会下发；实际路径由网关路由的 Predicate 决定。</div>
            </el-form-item>
            <el-form-item label="入参模式">
              <el-checkbox v-model="includeBodyFlag">请求体 JSON 作为参数基座（include-body）</el-checkbox>
              <div class="hint">
                勾选时请求体必须是 JSON <b>对象</b>（数组会 400），参数规则再逐条覆盖/补充；不勾选则只看参数规则取值。
              </div>
            </el-form-item>
            <el-form-item label="响应形态">
              <div class="row">
                <el-select v-model="form.responseMode" style="width: 240px">
                  <el-option v-for="m in RESPONSE_MODES" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
                <el-input-number v-model="form.successStatus" :min="0" :max="599" controls-position="right" style="width: 150px" />
                <span class="hint">成功状态码，0 = 默认 200</span>
              </div>
              <div class="hint">{{ RESPONSE_MODES.find((m) => m.value === form.responseMode)?.desc }}</div>
            </el-form-item>
            <el-form-item label="状态">
              <el-switch v-model="conversionEnabled" active-text="启用（参与导出）" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" />
            </el-form-item>
          </el-tab-pane>

          <!-- ------------------------------------------------ 参数映射 -->
          <el-tab-pane label="② 参数映射" name="args">
            <div class="args-toolbar">
              <div>
                <el-button size="small" type="primary" plain :disabled="!methodParams.length" @click="generateFromIdl">
                  从 IDL 方法生成参数行
                </el-button>
                <el-button size="small" @click="addArgRule">+ 加一条</el-button>
                <el-button size="small" :disabled="!argRules.length" @click="clearArgRules">清空</el-button>
              </div>
              <span class="hint">
                {{ methodParams.length ? `当前方法 ${form.methodName} 有 ${methodParams.length} 个 IDL 参数` : '选择「关联服务 + 方法」后可一键生成参数行' }}
              </span>
            </div>

            <el-table :data="argRules" size="small" border class="args-table">
              <el-table-column label="thrift 参数名" width="190">
                <template #default="{ row }">
                  <el-select
                    v-model="row.arg"
                    filterable
                    allow-create
                    default-first-option
                    size="small"
                    placeholder="IDL 参数名"
                  >
                    <el-option v-for="p in methodParams" :key="p.name" :label="`${p.name} · ${p.type}`" :value="p.name" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="来源" width="170">
                <template #default="{ row }">
                  <el-select v-model="row.source" size="small" @change="onSourceChange(row)">
                    <el-option v-for="s in ARGUMENT_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
                  </el-select>
                  <div class="cell-hint">{{ SOURCE_META[row.source]?.keyHint }}</div>
                </template>
              </el-table-column>
              <el-table-column label="取值 key" min-width="190">
                <template #default="{ row }">
                  <el-input
                    v-model="row.key"
                    size="small"
                    :disabled="SOURCE_META[row.source]?.keyDisabled"
                    :placeholder="row.source === 'FIXED' ? '常量无需 key' : `如 ${SOURCE_META[row.source]?.keyExample || row.arg}`"
                  />
                  <div class="cell-hint">留空则默认用 thrift 参数名</div>
                </template>
              </el-table-column>
              <el-table-column label="常量值" width="150">
                <template #default="{ row }">
                  <el-input v-model="row.value" size="small" :disabled="SOURCE_META[row.source]?.valueDisabled" placeholder="仅 FIXED" />
                </template>
              </el-table-column>
              <el-table-column label="默认值" width="150">
                <template #default="{ row }">
                  <el-input v-model="row.defaultValue" size="small" placeholder="取不到时兜底" />
                </template>
              </el-table-column>
              <el-table-column label="必填" width="65">
                <template #default="{ row }"><el-checkbox v-model="row.required" size="small" /></template>
              </el-table-column>
              <el-table-column label="" width="50">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="argRules.splice($index, 1)">删</el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-alert
              v-if="dupArgs.length"
              type="warning"
              :closable="false"
              :title="`参数名重复：${dupArgs.join('、')}。后面的规则会覆盖前面的取值，请确认是否有意如此。`"
              style="margin-top: 10px"
            />
            <div class="args-foot hint">
              取值优先级：规则取值（BODY / QUERY / HEADER / PATH / FIXED）→ 取不到时用默认值 → 仍没有且勾了必填则直接 400，不发 Thrift 调用。
            </div>
          </el-tab-pane>

          <!-- ------------------------------------------------ 预览与联调 -->
          <el-tab-pane label="③ 预览与联调" name="preview">
            <el-form-item label="网关地址">
              <el-input v-model="gatewayBase" placeholder="http://localhost:8080" style="width: 300px" />
              <span class="hint" style="margin-left: 10px">只影响下面生成的 curl 示例</span>
            </el-form-item>

            <div class="snippet">
              <div class="snippet-head">
                <span>① 路由过滤器短写 <code>ThriftGeneric</code></span>
                <el-button size="small" @click="copy(snippets.filter, '过滤器短写')">复制</el-button>
              </div>
              <pre>{{ snippets.filter }}</pre>
              <div class="hint">粘到网关路由的 filters 里；若路由页模板库没有 ThriftGeneric 模板，请手工新增过滤器或直接把下面的 YAML 写进网关配置文件。</div>
            </div>

            <div class="snippet">
              <div class="snippet-head">
                <span>② 路由配置（YAML 片段）</span>
                <el-button size="small" @click="copy(snippets.yaml, '路由 YAML')">复制</el-button>
              </div>
              <pre>{{ snippets.yaml }}</pre>
            </div>

            <div class="snippet">
              <div class="snippet-head">
                <span>③ 联调 curl（按参数规则自动生成示例值）</span>
                <el-button size="small" @click="copy(snippets.curl, 'curl 命令')">复制</el-button>
              </div>
              <pre>{{ snippets.curl }}</pre>
              <div class="hint">示例值按 IDL 参数类型推断，请按真实业务替换。PATH 来源要求路由 Path 里确实有同名 {变量}。</div>
            </div>

            <div class="snippet">
              <div class="snippet-head">
                <span>④ 本条规则会导出成什么（实时预览，未保存）</span>
                <el-button size="small" @click="copy(previewText, '配置预览')">复制</el-button>
              </div>
              <pre>{{ previewText }}</pre>
              <div class="hint">完整、权威的导出文本以页面顶部「导出预览」为准（由后端生成，含全部启用的服务与规则）。</div>
            </div>

            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="生效顺序"
              description="保存规则 → 在网关路由上挂 ThriftGeneric → 「配置同步」预览/下载/推送 → 重启网关（IDL 服务目录在启动期加载，当前版本不支持热注册）。"
              style="margin-top: 12px"
            />
          </el-tab-pane>
        </el-tabs>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button @click="tab = 'preview'">看预览</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存修改' : '创建规则' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayThriftApi } from '@/api/gateway'
import { ARGUMENT_SOURCES, DEFAULT_CONVERSION_FORM, RESPONSE_MODES, SOURCE_META, newArgRule } from './constants'
import { methodsOf } from './idl'
import { buildRulePreview, buildSnippets } from './export'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null },
  services: { type: Array, default: () => [] },
  conversions: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const isEdit = computed(() => !!props.row?.id)
const enabledServices = computed(() => (props.services || []).filter((s) => s.status === 1))

const formRef = ref()
const saving = ref(false)
const tab = ref('basic')
const gatewayBase = ref('http://localhost:8080')
const form = reactive(DEFAULT_CONVERSION_FORM())
const argRules = ref([])

const includeBodyFlag = computed({
  get: () => form.includeBody === 1,
  set: (v) => { form.includeBody = v ? 1 : 0 },
})
const conversionEnabled = computed({
  get: () => form.status === 1,
  set: (v) => { form.status = v ? 1 : 0 },
})

const selectedService = computed(() => (props.services || []).find((s) => s.serviceCode === form.serviceCode))
const methodOptions = computed(() => (selectedService.value?.idlContent ? methodsOf(selectedService.value.idlContent, form.serviceCode) : []))
const methodParams = computed(() => methodOptions.value.find((m) => m.name === form.methodName)?.params || [])
const dupArgs = computed(() => {
  const seen = new Set()
  const dups = new Set()
  argRules.value.forEach((r) => {
    if (!r.arg) return
    if (seen.has(r.arg)) dups.add(r.arg)
    seen.add(r.arg)
  })
  return [...dups]
})
const previewText = computed(() => buildRulePreview(form, argRules.value, props.services))
const snippets = computed(() => buildSnippets(form, argRules.value, props.services, gatewayBase.value))

const rules = {
  conversionCode: [{ validator: validateConversionCode, trigger: 'blur' }],
  serviceCode: [{ required: true, message: '请选择关联的 Thrift 服务', trigger: 'change' }],
}

function validateConversionCode(_rule, value, callback) {
  if (!value || !value.trim()) return callback(new Error('请填写规则名'))
  if (value.includes(',')) return callback(new Error('规则名不能包含逗号（路由短写用逗号分隔）'))
  const dup = (props.conversions || []).some((c) => c.conversionCode === value.trim() && c.id !== props.row?.id)
  if (dup) return callback(new Error('规则名已存在，不能重复'))
  callback()
}

function addArgRule() {
  argRules.value.push(newArgRule())
}

function clearArgRules() {
  argRules.value = []
}

function onSourceChange(row) {
  const meta = SOURCE_META[row.source]
  if (meta?.keyDisabled) row.key = ''
  if (meta?.valueDisabled) row.value = ''
}

function onServiceChange() {
  form.methodName = ''
  if (!form.httpPath && form.serviceCode) form.httpPath = `POST /thrift/${form.serviceCode}/{method}`
}

function onMethodChange() {
  if (form.methodName && form.httpPath && form.httpPath.includes('{method}')) {
    form.httpPath = form.httpPath.replace('{method}', form.methodName)
  }
}

/** 从 IDL 方法参数生成规则行：IDL 里 required 的参数自动勾必填，已有的同名行保留用户配置 */
function generateFromIdl() {
  if (!methodParams.value.length) return
  const existing = new Map(argRules.value.filter((r) => r.arg).map((r) => [r.arg, r]))
  const generated = methodParams.value.map((p) => existing.get(p.name) || newArgRule({ arg: p.name, source: 'BODY', key: '', required: p.required }))
  argRules.value = generated
  ElMessage.success(`已按 ${form.methodName} 生成 ${generated.length} 条参数行`)
  tab.value = 'args'
}

function copy(text, label) {
  if (!navigator.clipboard?.writeText) return ElMessage.warning('浏览器不支持自动复制，请手动选择文本')
  navigator.clipboard.writeText(text || '').then(
    () => ElMessage.success(`已复制${label || ''}`),
    () => ElMessage.warning('复制失败，请手动选择复制'),
  )
}

function validateArgRules() {
  for (let i = 0; i < argRules.value.length; i++) {
    const r = argRules.value[i]
    const hasOther = !!(r.key || r.value || r.defaultValue || r.required)
    if (!r.arg && hasOther) {
      tab.value = 'args'
      ElMessage.warning(`第 ${i + 1} 行没填 thrift 参数名，请补全或删掉该行`)
      return false
    }
    if (r.arg && r.source === 'FIXED' && !r.value && !r.defaultValue) {
      tab.value = 'args'
      ElMessage.warning(`第 ${i + 1} 行来源为 FIXED，但没填常量值或默认值`)
      return false
    }
  }
  return true
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const row = props.row || {}
    Object.assign(form, DEFAULT_CONVERSION_FORM(), {
      conversionCode: row.conversionCode || '',
      conversionName: row.conversionName || '',
      serviceCode: row.serviceCode || enabledServices.value[0]?.serviceCode || '',
      methodName: row.methodName || '',
      httpPath: row.httpPath || '',
      includeBody: row.includeBody == null ? 1 : row.includeBody,
      responseMode: row.responseMode || 'ENVELOPE',
      successStatus: row.successStatus == null ? 0 : row.successStatus,
      status: row.status == null ? 1 : row.status,
      remark: row.remark || '',
      argumentsJson: row.argumentsJson || '',
    })
    try {
      const parsed = JSON.parse(form.argumentsJson || '[]')
      argRules.value = Array.isArray(parsed)
        ? parsed.map((r) => newArgRule({
            arg: r.arg || '',
            source: r.source || 'BODY',
            key: r.key || '',
            value: r.value || '',
            required: !!r.required,
            defaultValue: r.defaultValue || r['default-value'] || '',
          }))
        : []
    } catch {
      argRules.value = []
    }
    tab.value = 'basic'
    formRef.value?.clearValidate()
  },
  { immediate: true },
)

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    tab.value = 'basic'
    ElMessage.warning('请先修正表单中标红的内容')
    return
  }
  if (!validateArgRules()) return

  const validRules = argRules.value.filter((r) => r.arg)
  const payload = {
    conversionCode: form.conversionCode.trim(),
    conversionName: form.conversionName.trim(),
    serviceCode: form.serviceCode,
    methodName: form.methodName || '',
    httpPath: form.httpPath || '',
    includeBody: form.includeBody,
    argumentsJson: JSON.stringify(validRules),
    responseMode: form.responseMode,
    successStatus: Number(form.successStatus) || 0,
    status: form.status,
    remark: form.remark,
  }
  saving.value = true
  try {
    if (isEdit.value) await gatewayThriftApi.updateConversion(props.row.id, payload)
    else await gatewayThriftApi.createConversion(payload)
    ElMessage.success(isEdit.value ? '规则已更新' : '规则已创建')
    visible.value = false
    emit('saved')
  } catch {
    // 拦截器已经统一提示后端错误
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dlg-body { max-height: 76vh; overflow-y: auto; padding-right: 6px; }
.row { display: flex; align-items: center; gap: 10px; width: 100%; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; line-height: 1.7; }
.bad-hint { color: #f56c6c; }
.cell-hint { color: #a8abb2; font-size: 11px; line-height: 1.4; margin-top: 2px; }
.args-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.args-foot { margin-top: 10px; }
.snippet { margin: 14px 0; border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden; }
.snippet-head { display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; background: #f5f7fa; font-size: 13px; font-weight: 600; color: #303133; }
.snippet pre { margin: 0; padding: 10px 12px; background: #fbfcfe; color: #2f3a4a; font-family: Consolas, Menlo, Monaco, monospace; font-size: 12px; line-height: 1.65; white-space: pre-wrap; word-break: break-all; }
.snippet .hint { padding: 6px 12px 8px; }
code { background: #eef1f6; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
