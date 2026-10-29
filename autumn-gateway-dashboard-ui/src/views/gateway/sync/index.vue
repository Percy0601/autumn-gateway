<template>
  <div class="gw-sync-page">
    <!-- 头部描述区 -->
    <el-card class="gw-sync-header" shadow="never">
      <div class="gw-sync-title">配置同步</div>
      <div class="gw-sync-desc">
        聚合当前网关的全部可视化配置（全局配置项、结构化全局配置、默认过滤器、路由及其断言/过滤器），
        生成一份可被 Spring Cloud Gateway 5.0.3 直接绑定的 properties 文本，可预览、下载，或推送到配置中心（Consul KV / Nacos）。
      </div>
      <div class="gw-sync-toolbar">
        <el-select v-model="profile" class="gw-sync-profile" @change="loadPreview">
          <el-option v-for="p in ['dev', 'test', 'prod']" :key="p" :label="p" :value="p" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadPreview">刷新预览</el-button>
        <el-button :disabled="!previewText" @click="copyText">复制</el-button>
        <el-button type="success" :disabled="!previewText" @click="download">下载 properties</el-button>
        <span class="gw-sync-push">
          <el-select v-model="centerId" class="gw-sync-center" placeholder="选择推送目标配置中心">
            <el-option v-for="c in centers" :key="c.id" :label="`${c.centerName}（${c.centerType} · ${c.host}:${c.port}）`" :value="c.id" />
          </el-select>
          <el-button type="warning" :disabled="!previewText || !centerId" :loading="pushing" @click="push">
            推送到配置中心
          </el-button>
        </span>
      </div>
    </el-card>

    <!-- 统计与警告 -->
    <el-card v-if="counts" class="gw-sync-counts" shadow="never">
      <div class="gw-sync-count" v-for="(v, k) in counts" :key="k">
        <div class="gw-sync-count-num">{{ v }}</div>
        <div class="gw-sync-count-label">{{ countLabel[k] || k }}</div>
      </div>
      <el-alert v-for="(w, i) in warns" :key="i" class="gw-sync-warn" type="warning" :closable="false" :title="w" />
    </el-card>

    <!-- 预览 -->
    <el-card v-loading="loading" class="gw-sync-preview" shadow="never">
      <div slot="header" class="gw-sync-preview-head">
        <span>预览（properties · spring.cloud.gateway.server.webflux.*）</span>
        <span class="gw-sync-preview-meta">{{ previewText ? `${previewText.split('\n').length} 行` : '' }}</span>
      </div>
      <el-input
        v-model="previewText"
        type="textarea"
        :rows="22"
        class="gw-sync-text"
        readonly
        placeholder="点击「刷新预览」生成网关配置文件"
      />
    </el-card>

    <!-- 推送结果 -->
    <el-card v-if="pushResult" class="gw-sync-result" shadow="never">
      <div class="gw-sync-result-title">最近推送结果</div>
      <el-alert
        :type="pushResult.success ? 'success' : 'error'"
        :closable="false"
        :title="pushResult.success ? '推送成功' : '推送失败'"
        :description="`${pushResult.centerName}（${pushResult.type}）→ ${pushResult.target || '-'}。${pushResult.message || ''}`"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayCenterApi, gatewaySyncApi } from '@/api/gateway'

const profile = ref('dev')
const centerId = ref<number | null>(null)
const centers = ref<any[]>([])
const previewText = ref('')
const counts = ref<Record<string, number> | null>(null)
const warns = ref<string[]>([])
const loading = ref(false)
const pushing = ref(false)
const pushResult = ref<any>(null)

const countLabel: Record<string, string> = {
  configItems: '配置项',
  structs: '结构化配置',
  globalFilters: '默认过滤器',
  routes: '路由',
  predicates: '断言',
  filters: '过滤器',
}

async function loadPreview() {
  loading.value = true
  try {
    const { data } = await gatewaySyncApi.preview(profile.value)
    previewText.value = data?.text || ''
    counts.value = data?.counts || null
    warns.value = data?.warn || []
  } catch (e: any) {
    ElMessage.error(`预览失败：${e?.message || e}`)
  } finally {
    loading.value = false
  }
}

function copyText() {
  navigator.clipboard?.writeText(previewText.value).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手动选择复制'),
  )
}

function download() {
  const blob = new Blob([previewText.value], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `gateway-${profile.value}.properties`
  a.click()
  URL.revokeObjectURL(url)
}

async function push() {
  if (!centerId.value) return
  pushing.value = true
  try {
    const { data } = await gatewaySyncApi.publish(profile.value, centerId.value)
    pushResult.value = data
    if (data?.success) ElMessage.success('已推送到配置中心')
    else ElMessage.error(data?.message || '推送失败')
  } catch (e: any) {
    pushResult.value = { success: false, message: String(e?.message || e) }
    ElMessage.error(`推送失败：${e?.message || e}`)
  } finally {
    pushing.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await gatewayCenterApi.list()
    centers.value = (data || []).filter((c: any) => c.status === 1)
  } catch {
    centers.value = []
  }
  loadPreview()
})
</script>

<style scoped>
.gw-sync-page {
  padding: 16px 16px 24px;
}
.gw-sync-header {
  margin-bottom: 16px;
}
.gw-sync-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.gw-sync-desc {
  margin-top: 8px;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 1.7;
  color: #909399;
}
.gw-sync-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.gw-sync-profile {
  width: 110px;
}
.gw-sync-center {
  width: 340px;
}
.gw-sync-push {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.gw-sync-counts {
  margin-bottom: 16px;
}
.gw-sync-counts :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  padding: 14px 20px;
}
.gw-sync-count-num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  line-height: 1.2;
}
.gw-sync-count-label {
  font-size: 12px;
  color: #909399;
}
.gw-sync-warn {
  margin-top: 6px;
}
.gw-sync-preview {
  margin-bottom: 16px;
}
.gw-sync-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #303133;
}
.gw-sync-preview-meta {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.gw-sync-text :deep(textarea) {
  font-family: Consolas, Menlo, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #f8f9fb;
}
.gw-sync-result-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}
</style>
