<template>
  <div class="page">
    <!-- ================= 配置闭环引导 ================= -->
    <el-card shadow="never" class="guide-card">
      <div class="guide-head">
        <div>
          <div class="guide-title">协议转换 · HTTP → Thrift 泛化调用</div>
          <div class="guide-desc">
            Dashboard 落库（gw_thrift_service / gw_thrift_conversion）→ 导出 autumn.thrift.* →「配置同步」下发 Consul / Nacos →
            thrift-autumn-gateway-starter 在网关侧按 IDL 泛化调用。下面 4 步走完才会真正生效。
          </div>
        </div>
        <div class="guide-actions">
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          <el-button size="small" @click="openPreview">导出预览</el-button>
          <el-button size="small" type="primary" @click="goTo('/gateway/sync/index')">去配置同步</el-button>
        </div>
      </div>

      <div class="steps">
        <div v-for="(s, i) in GUIDE_STEPS" :key="s.key" class="step" :class="`state-${guideState(s.key)}`">
          <div class="step-idx">{{ i + 1 }}</div>
          <div class="step-main">
            <div class="step-title">
              {{ s.title }}
              <el-tag size="small" :type="guideState(s.key) === 'done' ? 'success' : guideState(s.key) === 'manual' ? 'info' : 'warning'" effect="plain">
                {{ guideState(s.key) === 'done' ? '已完成' : guideState(s.key) === 'manual' ? '需手工确认' : '待处理' }}
              </el-tag>
            </div>
            <div class="step-desc">{{ s.desc }}</div>
          </div>
          <el-button link type="primary" @click="stepAction(s.key)">{{ s.action }} →</el-button>
        </div>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="最容易踩的坑"
        :description="'规则表里的「服务 / 方法 / HTTP 路径」不参与导出，只是标注。真正决定调用目标的是路由过滤器 ThriftGeneric=<service>,<method>,<规则名>；且 IDL 服务目录在网关启动期加载，改完配置必须下发并重启网关（当前版本不支持热注册）。'"
        style="margin-top: 12px"
      />
      <el-alert
        v-if="healthIssues.length"
        type="error"
        :closable="false"
        show-icon
        title="配置体检发现问题"
        style="margin-top: 8px"
      >
        <template #default>
          <div v-for="(issue, i) in healthIssues" :key="i" class="issue-line">· {{ issue }}</div>
          <div v-if="healthIssueTotal > healthIssues.length" class="issue-line">· 另有 {{ healthIssueTotal - healthIssues.length }} 条，展开导出预览逐条核对</div>
        </template>
      </el-alert>

      <el-collapse class="quick-ref">
        <el-collapse-item title="联调速查：autumn-service-framework（点开看可直接照抄的值）" name="ref">
          <div class="ref-grid">
            <div class="ref-k">联调环境</div>
            <div class="ref-v">Ubuntu 虚拟机 <code>192.168.0.102</code>，Consul 在 Docker（8500 从本机可达）；两个服务用 native image 在 screen 会话 <code>autumn-provider</code> / <code>autumn-consumer</code> 中启动，日志 <code>/tmp/autumn-*-native.log</code></div>
            <div class="ref-k">多网卡提醒</div>
            <div class="ref-v">provider 可能把 Docker 网桥地址（如 <code>172.17.0.1</code>）注册进 Consul；网关 P0 走静态 host/port，这里直接填可达 IP <code>192.168.0.102</code>，不依赖注册中心解析</div>
            <div class="ref-k">启动被测服务</div>
            <div class="ref-v">JVM 版 <code>./scripts/run-boot.sh autumn-sample-boot-provider</code>，native 版直接执行 <code>target/autumn-sample-boot-provider-native</code>（另一个是 consumer）；两者都用 <code>autumn.registry-type=consul</code> 注册。jar / native 都是 Java 25 编译，跑之前确认 <code>java -version</code></div>
            <div class="ref-k">Thrift 端口 / host</div>
            <div class="ref-v">provider <code>30880</code>、consumer <code>30881</code>。服务在虚拟机上时，「目标地址」的 host 填 <code>192.168.0.102</code>，不要填 127.0.0.1</div>
            <div class="ref-k">HTTP 端口</div>
            <div class="ref-v">provider <code>8080</code>、consumer <code>8081</code>；只有当网关和 provider 在同一台机器时才与网关端口冲突，需要换端口</div>
            <div class="ref-k">配置中心</div>
            <div class="ref-v">「配置同步 → 配置中心」填 <code>CONSUL</code> + <code>192.168.0.102:8500</code>；从网关所在机器验证过可达再下发</div>
            <div class="ref-k">IDL / 服务编码</div>
            <div class="ref-v">用「登记服务 → 插入示例 IDL → autumn-service-framework · SomeService」；服务编码填 <code>SomeService</code></div>
            <div class="ref-k">传输</div>
            <div class="ref-v">framed ✓、multiplexed ✓，multiplexed 服务名填 <code>xyz.wewin.autumn.sample.api.SomeService$Iface</code>（带 <code>$Iface</code>）；以 provider 启动日志 <code>autumn provider export service:</code> 打印的名字为准，不是 IDL 里的短名</div>
            <div class="ref-k">方法 / 参数</div>
            <div class="ref-v"><code>echo(1: string msg)</code>；规则参数名填 <code>msg</code>，来源 BODY 即可</div>
            <div class="ref-k">路由短写</div>
            <div class="ref-v"><code>ThriftGeneric=SomeService,echo,demoEcho</code></div>
            <div class="ref-k">验证请求</div>
            <div class="ref-v"><code>curl -XPOST 'http://&lt;网关地址&gt;/thrift/SomeService/echo' -H 'Content-Type: application/json' -d '{"msg":"hi"}'</code></div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- ================= 服务 ================= -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="hd">
          <div class="hd-left">
            <span class="hd-title">Thrift 服务</span>
            <el-tag size="small" type="info" effect="plain">{{ services.length }} 条 / 启用 {{ enabledServices.length }} 条</el-tag>
          </div>
          <div class="hd-right">
            <el-input v-model="serviceKeyword" clearable size="small" placeholder="搜服务编码 / 地址 / 备注" style="width: 220px" />
            <el-button size="small" type="primary" @click="openService()">登记服务</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredServices" border size="small" empty-text="还没有 Thrift 服务，点右上「登记服务」开始">
        <el-table-column label="服务编码（= IDL service 名）" min-width="190">
          <template #default="{ row }">
            <div class="mono">{{ row.serviceCode }}</div>
            <div class="sub">{{ row.serviceName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="目标地址" width="170">
          <template #default="{ row }"><span class="mono">{{ row.host }}:{{ row.port }}</span></template>
        </el-table-column>
        <el-table-column label="IDL 核对" min-width="190">
          <template #default="{ row }">
            <template v-if="idlInfo(row).state === 'unchecked'">
              <el-tag size="small" type="info" effect="plain">外部文件</el-tag>
              <div class="sub">{{ row.idlLocation }}</div>
            </template>
            <template v-else>
              <el-tag size="small" :type="idlInfo(row).state === 'ok' ? 'success' : 'danger'" effect="plain">
                {{ idlInfo(row).state === 'ok' ? '名称一致' : '名称不一致' }}
              </el-tag>
              <div class="sub">{{ idlInfo(row).methodCount }} 个方法 · 内联 IDL</div>
              <div v-if="idlInfo(row).state !== 'ok'" class="sub bad">IDL: {{ idlInfo(row).names.join('、') || '解析失败' }}</div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="传输" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.framed === 1 ? 'framed' : '裸 TSocket' }}</el-tag>
            <el-tag v-if="row.multiplexed === 1" size="small" effect="plain" style="margin-left: 4px">mux</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              size="small"
              @change="(v) => toggleEnabled(row, 'service', v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openIdl(row)">看 IDL</el-button>
            <el-button link type="primary" size="small" @click="openService(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeService(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ================= 规则 ================= -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="hd">
          <div class="hd-left">
            <span class="hd-title">转换规则（HTTP 入参 / 出参映射）</span>
            <el-tag size="small" type="info" effect="plain">{{ conversions.length }} 条 / 启用 {{ enabledConversions.length }} 条</el-tag>
          </div>
          <div class="hd-right">
            <el-input v-model="conversionKeyword" clearable size="small" placeholder="搜规则名 / 服务 / 方法" style="width: 220px" />
            <el-button size="small" type="primary" @click="openConversion()">新增规则</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="filteredConversions" border size="small" empty-text="还没有转换规则，点右上「新增规则」开始">
        <el-table-column label="规则名（路由第三参）" min-width="180">
          <template #default="{ row }">
            <div class="mono">{{ row.conversionCode }}</div>
            <div class="sub">{{ row.conversionName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="标注的目标" min-width="190">
          <template #default="{ row }">
            <div class="mono">{{ row.serviceCode }}{{ row.methodName ? '.' + row.methodName : '' }}</div>
            <div class="sub">{{ row.httpPath || '未填 HTTP 路径' }}（仅标注）</div>
          </template>
        </el-table-column>
        <el-table-column label="入参" width="160">
          <template #default="{ row }">
            {{ row.includeBody === 1 ? '请求体打底' : '仅规则取值' }} · {{ argCount(row) }} 条
          </template>
        </el-table-column>
        <el-table-column label="响应" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.responseMode || 'ENVELOPE' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              size="small"
              @change="(v) => toggleEnabled(row, 'conversion', v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="copyShortWrite(row)">复制短写</el-button>
            <el-button link type="primary" size="small" @click="openConversion(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="removeConversion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ================= 弹窗 ================= -->
    <ServiceDialog v-model="serviceDlg" :row="editingService" :services="services" @saved="load" />
    <ConversionDialog
      v-model="conversionDlg"
      :row="editingConversion"
      :services="services"
      :conversions="conversions"
      @saved="load"
    />

    <el-dialog v-model="previewDlg" title="autumn.thrift.* 导出预览（后端生成 · 权威文本）" width="900px" top="6vh">
      <div class="preview-bar">
        <el-tag v-if="previewCounts" size="small" effect="plain">启用服务 {{ previewCounts.thriftServices || 0 }}</el-tag>
        <el-tag v-if="previewCounts" size="small" effect="plain">启用规则 {{ previewCounts.thriftConversions || 0 }}</el-tag>
        <el-tag v-if="previewText" size="small" type="info" effect="plain">{{ previewText.split('\n').length }} 行</el-tag>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <el-button size="small" :disabled="!previewText" @click="copyText(previewText, '导出文本')">复制</el-button>
          <el-button size="small" :disabled="!previewText" @click="downloadPreview">下载 .properties</el-button>
          <el-button size="small" type="primary" @click="goTo('/gateway/sync/index')">去配置同步下发</el-button>
        </div>
      </div>
      <el-alert
        v-if="!previewText"
        type="warning"
        :closable="false"
        title="还没有启用中的 Thrift 服务，协议转换配置不会进入导出文本"
        style="margin-bottom: 10px"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        title="这段文本会与路由、全局配置一起由「配置同步」下发；注意导出时会自动写入 autumn.thrift.enabled=true"
        style="margin-bottom: 10px"
      />
      <el-input v-model="previewText" type="textarea" :rows="18" readonly class="mono-area" />
      <div class="hint" style="margin-top: 8px">
        下发后需重启网关：ThriftServiceCatalog 在启动期解析 IDL 建服务目录，当前版本不支持热注册。
      </div>
    </el-dialog>

    <el-dialog v-model="idlDlg" :title="`IDL · ${idlRow?.serviceCode || ''}`" width="760px">
      <el-alert
        v-if="idlRow && idlInfo(idlRow).state === 'mismatch'"
        type="error"
        :closable="false"
        title="服务编码与 IDL 里解析出的 service 名不一致，网关启动会失败"
        style="margin-bottom: 10px"
      />
      <el-input :model-value="idlRow?.idlContent || idlRow?.idlLocation || ''" type="textarea" :rows="14" readonly class="mono-area" />
      <template #footer>
        <el-button @click="copyText(idlRow?.idlContent || idlRow?.idlLocation || '', 'IDL')">复制</el-button>
        <el-button type="primary" @click="idlDlg = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { gatewayThriftApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'
import ServiceDialog from './ServiceDialog.vue'
import ConversionDialog from './ConversionDialog.vue'
import { GUIDE_STEPS } from './constants'
import { checkServiceCode, parseIdl } from './idl'
import { thriftGenericValue } from './export'

const router = useRouter()
const loading = ref(false)
const services = ref([])
const conversions = ref([])
const serviceKeyword = ref('')
const conversionKeyword = ref('')

const serviceDlg = ref(false)
const editingService = ref(null)
const conversionDlg = ref(false)
const editingConversion = ref(null)

const previewDlg = ref(false)
const previewText = ref('')
const previewCounts = ref(null)
const idlDlg = ref(false)
const idlRow = ref(null)

const enabledServices = computed(() => services.value.filter((s) => s.status === 1))
const enabledConversions = computed(() => conversions.value.filter((c) => c.status === 1))
const filteredServices = computed(() => matchKeyword(services.value, serviceKeyword.value, ['serviceCode', 'serviceName', 'host', 'remark']))
const filteredConversions = computed(() => matchKeyword(conversions.value, conversionKeyword.value, ['conversionCode', 'conversionName', 'serviceCode', 'methodName', 'httpPath']))

function matchKeyword(list, keyword, fields) {
  const k = String(keyword || '').trim().toLowerCase()
  if (!k) return list
  return list.filter((row) => fields.some((f) => String(row[f] || '').toLowerCase().includes(k)))
}

function idlInfo(row) {
  if (!row?.idlContent) return { state: 'unchecked', names: [], methodCount: 0 }
  const parsed = parseIdl(row.idlContent)
  const check = checkServiceCode(row.idlContent, row.serviceCode)
  return {
    state: check.state === 'ok' ? 'ok' : 'mismatch',
    names: check.names,
    methodCount: parsed.services.reduce((n, s) => n + s.methods.length, 0),
  }
}

/** 配置体检：把"保存成功但下发出去会炸"的问题提前摆到页面上 */
const healthIssues = computed(() => {
  const issues = []
  services.value.forEach((s) => {
    const info = idlInfo(s)
    if (info.state === 'mismatch') issues.push(`服务 ${s.serviceCode}：IDL 里的 service 名是 ${info.names.join('、') || '解析失败'}，编码对不上，网关启动会失败`)
  })
  conversions.value.forEach((c) => {
    if (String(c.conversionCode || '').includes(',')) issues.push(`规则 ${c.conversionCode}：名字含逗号，路由短写会被拆成多个参数`)
    const svc = services.value.find((s) => s.serviceCode === c.serviceCode)
    if (!svc) issues.push(`规则 ${c.conversionCode}：关联的 Thrift 服务 ${c.serviceCode} 未登记（标注失效，不影响导出）`)
    else if (svc.status !== 1) issues.push(`规则 ${c.conversionCode}：关联服务 ${c.serviceCode} 已停用`)
    if (svc?.idlContent && c.methodName) {
      const methods = parseIdl(svc.idlContent).services.flatMap((s) => s.methods.map((m) => m.name))
      if (methods.length && !methods.includes(c.methodName)) issues.push(`规则 ${c.conversionCode}：方法 ${c.methodName} 不在 ${c.serviceCode} 的 IDL 方法列表里`)
    }
  })
  return issues
})
const healthIssueTotal = computed(() => healthIssues.value.length)

function guideState(key) {
  if (key === 'service') return enabledServices.value.length ? 'done' : 'todo'
  if (key === 'mapping') return enabledConversions.value.length ? 'done' : 'todo'
  return 'manual'
}

function stepAction(key) {
  if (key === 'service') openService()
  else if (key === 'mapping') openConversion()
  else if (key === 'route') goTo('/gateway/route/index')
  else if (key === 'publish') goTo('/gateway/sync/index')
}

function goTo(path) {
  router.push(path).catch(() => ElMessage.warning(`请从左侧菜单打开：${path}`))
}

async function load() {
  loading.value = true
  try {
    const [s, c] = await Promise.all([gatewayThriftApi.services(), gatewayThriftApi.conversions()])
    services.value = s.data || []
    conversions.value = c.data || []
  } catch {
    // 拦截器已提示
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

function openService(row = null) {
  editingService.value = row
  serviceDlg.value = true
}
function openConversion(row = null) {
  editingConversion.value = row
  conversionDlg.value = true
}
function openIdl(row) {
  idlRow.value = row
  idlDlg.value = true
}

function servicePayload(row, patch = {}) {
  return {
    serviceCode: row.serviceCode, serviceName: row.serviceName || '', host: row.host, port: row.port,
    idlLocation: row.idlLocation || '', idlContent: row.idlContent || '',
    multiplexedName: row.multiplexedName || '', framed: row.framed, multiplexed: row.multiplexed,
    status: row.status, remark: row.remark || '', ...patch,
  }
}
function conversionPayload(row, patch = {}) {
  return {
    conversionCode: row.conversionCode, conversionName: row.conversionName || '',
    serviceCode: row.serviceCode, methodName: row.methodName || '', httpPath: row.httpPath || '',
    includeBody: row.includeBody, argumentsJson: row.argumentsJson || '[]',
    responseMode: row.responseMode || 'ENVELOPE', successStatus: row.successStatus || 0,
    status: row.status, remark: row.remark || '', ...patch,
  }
}

/** 列表里直接启停，不必进弹窗；失败把开关回滚 */
async function toggleEnabled(row, kind, value) {
  try {
    if (kind === 'service') await gatewayThriftApi.updateService(row.id, servicePayload(row, { status: value }))
    else await gatewayThriftApi.updateConversion(row.id, conversionPayload(row, { status: value }))
    ElMessage.success(value === 1 ? '已启用，会进入导出文本' : '已停用，不会进入导出文本')
  } catch {
    row.status = value === 1 ? 0 : 1
  }
}

async function removeService(row) {
  const used = conversions.value.filter((c) => c.serviceCode === row.serviceCode).length
  const extra = used ? `\n有 ${used} 条转换规则标注了它（规则本身仍会导出，但标注会失效）。` : ''
  if (!await isConfirmed(`删除 Thrift 服务「${row.serviceCode}」？${extra}\n删除后该服务不再进入导出文本，网关重启后将找不到它。`)) return
  try {
    await gatewayThriftApi.removeService(row.id)
    ElMessage.success('已删除，记得重新下发配置并重启网关')
    load()
  } catch { /* 拦截器已提示 */ }
}

async function removeConversion(row) {
  if (!await isConfirmed(`删除转换规则「${row.conversionCode}」？\n如果已有路由的 ThriftGeneric 第三参引用了它，网关启动会报 unknown_mapping。`)) return
  try {
    await gatewayThriftApi.removeConversion(row.id)
    ElMessage.success('已删除，记得重新下发配置并重启网关')
    load()
  } catch { /* 拦截器已提示 */ }
}

async function openPreview() {
  try {
    const res = await gatewayThriftApi.preview()
    previewText.value = res.data?.text || ''
    previewCounts.value = res.data?.counts || null
    previewDlg.value = true
  } catch { /* 拦截器已提示 */ }
}

function copyText(text, label) {
  if (!navigator.clipboard?.writeText) return ElMessage.warning('浏览器不支持自动复制，请手动选择文本')
  navigator.clipboard.writeText(text || '').then(
    () => ElMessage.success(`已复制${label || ''}`),
    () => ElMessage.warning('复制失败，请手动选择复制'),
  )
}

function copyShortWrite(row) {
  copyText(`ThriftGeneric=${thriftGenericValue(row)}`, '过滤器短写')
}

function downloadPreview() {
  const blob = new Blob([previewText.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'autumn-thrift.properties'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.page { padding: 4px; }
.guide-card { margin-bottom: 14px; }
.guide-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.guide-title { font-size: 17px; font-weight: 600; color: #303133; }
.guide-desc { margin-top: 6px; font-size: 12px; line-height: 1.8; color: #909399; max-width: 900px; }
.guide-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.steps { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 14px; }
.step { display: flex; align-items: flex-start; gap: 10px; padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 6px; background: #fff; }
.step.state-done { border-color: #d1eddb; background: #f7fcf9; }
.step.state-todo { border-color: #f5dfb8; background: #fffcf6; }
.step-idx { flex: none; width: 22px; height: 22px; border-radius: 50%; background: #409eff; color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center; font-weight: 600; }
.state-done .step-idx { background: #67c23a; }
.state-todo .step-idx { background: #e6a23c; }
.state-manual .step-idx { background: #909399; }
.step-main { flex: 1; min-width: 0; }
.step-title { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: #303133; flex-wrap: wrap; }
.step-desc { margin-top: 4px; font-size: 12px; line-height: 1.6; color: #909399; }
.issue-line { font-size: 12px; line-height: 1.8; }
.quick-ref { margin-top: 12px; }
.quick-ref :deep(.el-collapse-item__header) { font-size: 13px; color: #409eff; }
.quick-ref :deep(.el-collapse-item__content) { padding-bottom: 10px; }
.ref-grid { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 8px 14px; font-size: 12px; line-height: 1.7; }
.ref-k { color: #909399; }
.ref-v { color: #303133; word-break: break-all; }
code { background: #eef1f6; padding: 1px 4px; border-radius: 3px; font-size: 12px; font-family: Consolas, Menlo, Monaco, monospace; }
.block-card { margin-bottom: 14px; }
.hd { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.hd-left, .hd-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hd-title { font-weight: 600; color: #303133; }
.mono { font-family: Consolas, Menlo, Monaco, monospace; font-size: 12px; color: #303133; }
.sub { color: #a8abb2; font-size: 11px; line-height: 1.5; }
.bad { color: #f56c6c; }
.hint { color: #909399; font-size: 12px; line-height: 1.7; }
.preview-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.mono-area :deep(textarea) { font-family: Consolas, Menlo, Monaco, monospace; font-size: 12px; line-height: 1.6; background: #f8f9fb; color: #2f3a4a; }
@media (max-width: 1100px) { .steps { grid-template-columns: 1fr; } }
</style>
