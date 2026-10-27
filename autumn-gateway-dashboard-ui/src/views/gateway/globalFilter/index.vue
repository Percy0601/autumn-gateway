<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayGlobalFilterApi } from '@/api/gateway'
import { isConfirmed } from '@/utils/confirm'

const loading = ref(false)
const rows = ref([])

async function loadData() {
  loading.value = true
  try {
    const body = await gatewayGlobalFilterApi.list()
    rows.value = body.data || []
  } finally {
    loading.value = false
  }
}
onMounted(loadData)

const drawerOpen = ref(false)
const saving = ref(false)
const currentId = ref(null)
const form = reactive({
  filterName: '',
  argsJson: '',
  sortNum: 0,
  enabled: 1,
  remark: '',
})

function openCreate() {
  currentId.value = null
  Object.assign(form, { filterName: '', argsJson: '', sortNum: 0, enabled: 1, remark: '' })
  drawerOpen.value = true
}
function openEdit(row) {
  currentId.value = row.id
  Object.assign(form, { filterName: row.filterName, argsJson: row.argsJson || '', sortNum: row.sortNum ?? 0, enabled: row.enabled ?? 1, remark: row.remark || '' })
  drawerOpen.value = true
}

async function save() {
  saving.value = true
  try {
    if (currentId.value) await gatewayGlobalFilterApi.update(currentId.value, form)
    else await gatewayGlobalFilterApi.create(form)
    ElMessage.success('已保存')
    drawerOpen.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  const ok = await isConfirmed(`删除全局过滤器 ${row.filterName}？`)
  if (!ok) return
  await gatewayGlobalFilterApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <div class="mb-3 flex justify-between">
        <span class="text-base font-bold">全局默认过滤器（default-filters）</span>
        <el-button type="primary" size="small" @click="openCreate">添加过滤器</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="sortNum" label="顺序" width="70" />
        <el-table-column prop="filterName" label="工厂" width="200" />
        <el-table-column prop="argsJson" label="参数 JSON" min-width="280" show-overflow-tooltip />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="drawerOpen" :title="currentId ? '编辑全局过滤器' : '添加全局过滤器'" size="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="工厂" required>
          <el-input v-model="form.filterName" placeholder="如 AddResponseHeader / RequestRateLimiter" />
        </el-form-item>
        <el-form-item label="参数 JSON">
          <el-input v-model="form.argsJson" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="顺序">
          <el-input-number v-model="form.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
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
