<template>
  <div class="page">
    <!-- ============ 说明 + 全局操作 ============ -->
    <el-card shadow="never" class="head-card">
      <div class="head">
        <div>
          <div class="title">服务治理 · autumn service framework</div>
          <div class="desc">
            Dashboard 通过 Thrift 远程 invoke 各实例的控制面 ControlApi（默认 :30880），实时读取 health / applicationInfo / providerInfo。
            度量来自框架 AutumnMetrics：连接池 borrowing / succeeded / discarded，按服务名聚合，<b>不落库、每次刷新重新采集</b>。
          </div>
        </div>
        <div class="actions">
          <el-switch v-model="autoRefresh" active-text="10s 自动刷新" @change="toggleAuto" />
          <el-button :loading="loading" @click="load">立即刷新</el-button>
          <el-button type="primary" @click="openCreate">登记实例</el-button>
          <el-button @click="openInvoke">Invoke 控制台</el-button>
        </div>
      </div>
      <div class="sub">最近采集：{{ lastRefresh || '—' }} ｜ 控制面鉴权默认关闭，直连目标实例控制面（不发送 optional token 字段）</div>
    </el-card>

    <!-- ============ 概览指标 ============ -->
    <el-row :gutter="12" class="stat-row">
      <el-col v-for="s in statCards" :key="s.label" :xs="12" :sm="6" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-hint">{{ s.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <!-- ============ 左：实例登记 ============ -->
      <el-col :xs="24" :sm="24" :md="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>服务实例</span>
              <span class="muted">控制面无「枚举实例」接口，需在此登记地址</span>
            </div>
          </template>
          <el-table
            v-loading="loading"
            :data="instances"
            highlight-current-row
            size="small"
            empty-text="尚未登记实例，点右上角「登记实例」添加"
            @current-change="onSelect"
          >
            <el-table-column prop="serviceName" label="服务名" min-width="120" show-overflow-tooltip />
            <el-table-column label="地址" min-width="130">
              <template #default="{ row }">{{ row.host }}:{{ row.controlPort > 0 ? row.controlPort : row.port }}</template>
            </el-table-column>
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag size="small" :type="statusOf(row) === 'offline' ? 'danger' : statusOf(row) === 'disabled' ? 'info' : 'success'" effect="plain">
                  {{ statusOf(row) === 'offline' ? '不可达' : statusOf(row) === 'disabled' ? '已停用' : '在线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="116" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="removeInstance(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-alert
            class="tip"
            type="info"
            :closable="false"
            title="连接约定"
            description="TFramedTransport + TBinaryProtocol + TMultiplexedProtocol，复用名 xyz.wewin.autumn.api.ControlApi$Iface。独立控制面端口（controlPort）优先于业务端口。"
          />
        </el-card>
      </el-col>

      <!-- ============ 右：观测详情 ============ -->
      <el-col :xs="24" :sm="24" :md="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>观测详情</span>
              <span class="muted">{{ selected ? selected.serviceName : '点击左侧实例查看' }}</span>
            </div>
          </template>

          <template v-if="selected">
            <el-descriptions :column="2" size="small" border class="desc-block">
              <el-descriptions-item label="健康状态">
                <el-tag size="small" :type="selected.healthy ? 'success' : 'danger'" effect="plain">
                  {{ selected.healthy ? 'OK' : (selected.error || 'DOWN') }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="采集耗时">{{ selected.elapsedMillis }} ms</el-descriptions-item>
              <el-descriptions-item label="目标地址">{{ selected.host }}:{{ selected.port }}</el-descriptions-item>
              <el-descriptions-item label="度量服务数">{{ (selected.metrics || []).length }}</el-descriptions-item>
              <el-descriptions-item label="ProviderConfig" :span="2">
                <span class="mono">{{ configText(selected.providerConfig) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="ApplicationInfo" :span="2">
                <span class="mono wrap">{{ selected.applicationInfo || selected.error || '—' }}</span>
              </el-descriptions-item>
            </el-descriptions>

            <div class="section-title">调用度量（按服务）</div>
            <el-table :data="selected.metrics || []" size="small" empty-text="该实例暂无度量数据（连接池尚未产生调用）">
              <el-table-column prop="service" label="服务" min-width="180" show-overflow-tooltip />
              <el-table-column prop="borrowed" label="借用" width="80" />
              <el-table-column prop="succeeded" label="成功归还" width="94" />
              <el-table-column prop="discarded" label="异常丢弃" width="94" />
              <el-table-column label="成功率" width="92">
                <template #default="{ row }">
                  <span :class="row.successRate < 90 ? 'bad' : 'good'">{{ row.successRate }}%</span>
                </template>
              </el-table-column>
              <el-table-column label="丢弃率" width="92">
                <template #default="{ row }">
                  <span :class="row.discardRate > 5 ? 'bad' : ''">{{ row.discardRate }}%</span>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="点击左侧实例，查看健康状态与调用度量" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 全局度量图表 ============ -->
    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="card-head">
          <span>全部实例 · 连接池调用度量</span>
          <span class="muted">按借用次数取前 12 个服务</span>
        </div>
      </template>
      <div ref="chartEl" class="chart"></div>
      <el-empty v-if="!metricsRows.length" description="暂无度量数据" :image-size="60" />
    </el-card>

    <!-- ============ 实例登记弹窗 ============ -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑服务实例' : '登记服务实例'" width="520px">
      <el-form :model="form" label-width="110px" size="small">
        <el-form-item label="服务名" required>
          <el-input v-model="form.serviceName" placeholder="展示名 / 注册中心 application.name" />
        </el-form-item>
        <el-form-item label="Host" required>
          <el-input v-model="form.host" placeholder="127.0.0.1" />
        </el-form-item>
        <el-form-item label="业务端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
          <span class="muted form-hint">框架默认 30880</span>
        </el-form-item>
        <el-form-item label="控制面端口">
          <el-input-number v-model="form.controlPort" :min="0" :max="65535" controls-position="right" />
          <span class="muted form-hint">0 或留空 = 与业务端口共用</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveInstance">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ Invoke 控制台 ============ -->
    <el-drawer v-model="invokeVisible" title="控制面 Invoke 控制台" size="46%">
      <el-form :model="invokeForm" label-width="100px" size="small">
        <el-form-item label="目标实例" required>
          <el-select v-model="invokeForm.instanceId" placeholder="选择实例" style="width: 100%">
            <el-option v-for="i in instances" :key="i.id" :label="`${i.serviceName} (${i.host}:${i.controlPort > 0 ? i.controlPort : i.port})`" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="方法" required>
          <el-select v-model="invokeForm.method" style="width: 100%" @change="invokeResult = ''">
            <el-option v-for="m in methods" :key="m.name" :label="`${m.label} · ${m.name}`" :value="m.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-for="p in currentMethodParams" :key="p.key" :label="p.label" :required="p.required">
          <el-input v-model="invokeParams[p.key]" :placeholder="p.key" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="invoking" @click="doInvoke">发起调用</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="currentMethod" type="info" :closable="false" :description="currentMethod.description" />
      <div class="section-title">原始返回</div>
      <pre class="raw">{{ invokeResult || '—' }}</pre>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { gatewayGovernanceApi as api } from '@/api/gateway'

const loading = ref(false)
const saving = ref(false)
const invoking = ref(false)
const instances = ref([])
const overviewRows = ref([])
const methods = ref([])
const selectedId = ref(null)
const lastRefresh = ref('')
const autoRefresh = ref(false)
let refreshTimer = null

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ serviceName: '', host: '127.0.0.1', port: 30880, controlPort: 0, status: 1, remark: '' })

const invokeVisible = ref(false)
const invokeForm = reactive({ instanceId: null, method: 'providerInfo' })
const invokeParams = reactive({ name: '', ip: '', port: '30880' })
const invokeResult = ref('')

const chartEl = ref(null)
let chart = null

/** 统一拆包：request 拦截器返回整个 {code,message,data,total} */
async function unwrap(promise, fallback) {
  try {
    const res = await promise
    if (res && res.code !== undefined && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return fallback
    }
    return res && res.data !== undefined ? res.data : fallback
  } catch (e) {
    ElMessage.error((e && e.message) || '请求失败')
    return fallback
  }
}

const observeMap = computed(() => {
  const m = {}
  for (const row of overviewRows.value) m[row.instanceId] = row
  return m
})

const selected = computed(() => (selectedId.value ? observeMap.value[selectedId.value] : null) || null)

/** 汇总所有实例的度量，图表用；保留实例名避免同名服务混淆 */
const metricsRows = computed(() => {
  const rows = []
  for (const row of overviewRows.value) {
    for (const m of row.metrics || []) {
      rows.push({ ...m, key: `${row.serviceName} / ${m.service}` })
    }
  }
  return rows.sort((a, b) => b.borrowed - a.borrowed)
})

const statCards = computed(() => {
  const total = instances.value.length
  const reachable = overviewRows.value.filter(r => r.reachable).length
  const healthy = overviewRows.value.filter(r => r.healthy).length
  const borrowed = metricsRows.value.reduce((sum, m) => sum + (m.borrowed || 0), 0)
  return [
    { label: '登记实例', value: total, hint: '启用的参与采集', color: '#303133' },
    { label: '可达 / 健康', value: `${reachable} / ${healthy}`, hint: 'ControlApi 连通', color: reachable === total && total > 0 ? '#67c23a' : '#e6a23c' },
    { label: '不可达', value: overviewRows.value.filter(r => !r.reachable).length, hint: '检查端口与连通性', color: '#f56c6c' },
    { label: '累计借用', value: borrowed, hint: `涉及 ${metricsRows.value.length} 个服务`, color: '#409eff' },
  ]
})

const currentMethod = computed(() => methods.value.find(m => m.name === invokeForm.method) || null)
const currentMethodParams = computed(() => currentMethod.value?.params || [])

function statusOf(row) {
  if (row.status === 0) return 'disabled'
  const o = observeMap.value[row.id]
  return o && !o.reachable ? 'offline' : 'online'
}

function configText(config) {
  const entries = Object.entries(config || {})
  return entries.length ? entries.map(([k, v]) => `${k}=${v}`).join('  ') : '—'
}

async function load() {
  loading.value = true
  try {
    const [list, overview, methodList] = await Promise.all([
      unwrap(api.instances(), []),
      unwrap(api.overview(), []),
      unwrap(api.methods(), []),
    ])
    instances.value = list || []
    overviewRows.value = overview || []
    methods.value = methodList || []
    if (!selectedId.value && instances.value.length) selectedId.value = instances.value[0].id
    lastRefresh.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

function onSelect(row) {
  if (row) selectedId.value = row.id
}

function toggleAuto(on) {
  stopAuto()
  if (on) refreshTimer = setInterval(load, 10000)
}

function stopAuto() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { serviceName: '', host: '127.0.0.1', port: 30880, controlPort: 0, status: 1, remark: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    serviceName: row.serviceName, host: row.host, port: row.port || 30880,
    controlPort: row.controlPort || 0, status: row.status ?? 1, remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function saveInstance() {
  if (!form.serviceName.trim() || !form.host.trim()) {
    ElMessage.warning('服务名与 Host 不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, serviceName: form.serviceName.trim(), host: form.host.trim(), controlPort: form.controlPort || null }
    const res = editingId.value
      ? await api.updateInstance(editingId.value, payload)
      : await api.createInstance(payload)
    if (res && res.code !== 200) {
      ElMessage.error(res.message || '保存失败')
      return
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e && e.message) || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeInstance(row) {
  try {
    await ElMessageBox.confirm(`确认删除实例「${row.serviceName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  const res = await api.removeInstance(row.id)
  if (res && res.code !== 200) {
    ElMessage.error(res.message || '删除失败')
    return
  }
  if (selectedId.value === row.id) selectedId.value = null
  await load()
}

function openInvoke() {
  if (!invokeForm.instanceId && instances.value.length) invokeForm.instanceId = instances.value[0].id
  invokeResult.value = ''
  invokeVisible.value = true
}

async function doInvoke() {
  if (!invokeForm.instanceId) {
    ElMessage.warning('请选择目标实例')
    return
  }
  invoking.value = true
  try {
    const params = {}
    for (const p of currentMethodParams.value) params[p.key] = invokeParams[p.key]
    const res = await api.invoke({ instanceId: invokeForm.instanceId, method: invokeForm.method, params })
    if (res && res.code !== 200) {
      ElMessage.error(res.message || '调用失败')
      return
    }
    const data = res?.data || {}
    invokeResult.value = `[${data.method}] ${data.elapsedMillis} ms\n\n${data.result}`
  } catch (e) {
    invokeResult.value = String((e && e.message) || e)
  } finally {
    invoking.value = false
  }
}

function renderChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const rows = metricsRows.value.slice(0, 12)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['借用', '成功归还', '异常丢弃'], top: 0 },
    grid: { left: 52, right: 20, top: 34, bottom: 78 },
    xAxis: {
      type: 'category',
      data: rows.map(r => r.service),
      axisLabel: { rotate: 28, formatter: v => (v.length > 18 ? v.slice(0, 16) + '…' : v) },
    },
    yAxis: { type: 'value', name: '次数' },
    series: [
      { name: '借用', type: 'bar', data: rows.map(r => r.borrowed), itemStyle: { color: '#409eff' } },
      { name: '成功归还', type: 'bar', data: rows.map(r => r.succeeded), itemStyle: { color: '#67c23a' } },
      { name: '异常丢弃', type: 'bar', data: rows.map(r => r.discarded), itemStyle: { color: '#f56c6c' } },
    ],
  }, true)
}

function onResize() {
  chart && chart.resize()
}

watch(metricsRows, () => nextTick(renderChart), { deep: true })
onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  stopAuto()
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
  chart = null
})
</script>

<style scoped>
.page { padding: 12px; }
.head-card { margin-bottom: 12px; }
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.title { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.desc { color: #606266; font-size: 12px; line-height: 1.7; max-width: 760px; }
.actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.sub { margin-top: 8px; color: #909399; font-size: 12px; }
.stat-row { margin-bottom: 12px; }
.stat-card { text-align: center; }
.stat-label { color: #909399; font-size: 12px; }
.stat-value { font-size: 24px; font-weight: 600; line-height: 1.5; }
.stat-hint { color: #c0c4cc; font-size: 11px; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; font-weight: normal; }
.tip { margin-top: 12px; }
.desc-block { margin-bottom: 14px; }
.section-title { font-size: 13px; font-weight: 600; margin: 14px 0 8px; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.wrap { display: inline-block; white-space: pre-wrap; word-break: break-all; max-height: 96px; overflow: auto; }
.good { color: #67c23a; }
.bad { color: #f56c6c; }
.chart-card { margin-top: 12px; }
.chart { height: 280px; width: 100%; }
.form-hint { margin-left: 8px; }
.raw { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 320px; overflow: auto; font-family: Consolas, Monaco, monospace; }
</style>
