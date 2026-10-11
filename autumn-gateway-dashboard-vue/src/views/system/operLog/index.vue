<script setup>
import { onMounted, reactive, ref } from 'vue'
import { basicOperLogApi } from '../../../api/basic'
import { fmtDateTime, OPER_STATUS_TAG, operStatusLabel } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  appCode: '',
  title: '',
  businessType: '',
  status: '',
  startTime: '',
  endTime: '',
})

const dateRange = ref([])

function fmtDate(d) {
  if (!d) return ''
  const dt = new Date(d)
  const p = (n) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      current: query.current,
      pageSize: query.pageSize,
      appCode: query.appCode || undefined,
      title: query.title || undefined,
      businessType: query.businessType || undefined,
      status: query.status === '' ? undefined : Number(query.status),
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
    }
    const body = await basicOperLogApi.list(params)
    rows.value = body.data || []
    total.value = body.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  if (dateRange.value && dateRange.value.length === 2) {
    query.startTime = fmtDate(dateRange.value[0])
    query.endTime = fmtDate(dateRange.value[1])
  } else {
    query.startTime = ''
    query.endTime = ''
  }
  loadData()
}

function handleReset() {
  query.appCode = ''
  query.title = ''
  query.businessType = ''
  query.status = ''
  query.startTime = ''
  query.endTime = ''
  dateRange.value = []
  handleSearch()
}

// ---------- 详情 ----------
const detailOpen = ref(false)
const currentLog = ref(null)

function openDetail(row) {
  currentLog.value = row
  detailOpen.value = true
}

function statusType(s) {
  return OPER_STATUS_TAG[s] || 'info'
}

onMounted(loadData)
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <div class="pro-card-head">
        <div class="pro-card-title">操作日志</div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="系统标识">
          <el-input
            v-model="query.appCode"
            placeholder="如 order"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="标题">
          <el-input
            v-model="query.title"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input
            v-model="query.businessType"
            placeholder="请输入"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option :value="0" label="正常" />
            <el-option :value="1" label="异常" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="appCode" label="系统" min-width="90" />
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="businessType" label="业务类型" min-width="120" show-overflow-tooltip />
          <el-table-column prop="requestMethod" label="方法" width="80" align="center" />
          <el-table-column prop="requestUrl" label="请求URL" min-width="220" show-overflow-tooltip />
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">{{ row.consumeTime }} ms</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">
                {{ operStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operIp" label="操作IP" width="130" />
          <el-table-column label="操作时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.opTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openDetail(row)">详情</el-link>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pro-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <!-- 详情抽屉（只读） -->
    <el-drawer v-model="detailOpen" title="日志详情" size="560px">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="系统标识">{{ currentLog.appCode }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ currentLog.title }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ currentLog.businessType }}</el-descriptions-item>
        <el-descriptions-item label="请求ID">{{ currentLog.requestId }}</el-descriptions-item>
        <el-descriptions-item label="请求URL">{{ currentLog.requestUrl }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="执行方法">{{ currentLog.classMethod }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.consumeTime }} ms</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(currentLog.status)" size="small">
            {{ operStatusLabel(currentLog.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operId }}</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ currentLog.operIp }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ fmtDateTime(currentLog.opTime) }}</el-descriptions-item>
        <el-descriptions-item label="请求数据">
          <pre class="log-pre">{{ currentLog.requestData || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="响应结果">
          <pre class="log-pre">{{ currentLog.responseData || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentLog.status === 1" label="错误信息">
          <pre class="log-pre error">{{ currentLog.errorMsg || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.log-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
}

.log-pre.error {
  color: #cf1322;
}
</style>
