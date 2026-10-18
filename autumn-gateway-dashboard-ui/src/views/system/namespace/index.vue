<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { basicNamespaceApi } from '../../../api/basic'
import { copyText, fmtDateTime } from '../../../utils/format'
import { isConfirmed } from '../../../utils/confirm'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  code: '',
  name: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicNamespaceApi.list(query)
    rows.value = body.data || []
    total.value = body.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadData()
}

function handleReset() {
  query.code = ''
  query.name = ''
  handleSearch()
}

async function changeStatus(row) {
  const disable = row.status === 1
  const ok = await isConfirmed(`此操作将${disable ? '禁用' : '启用'}命名空间「${row.name}」，是否继续？`)
  if (!ok) return
  if (disable) await basicNamespaceApi.disable(row.id)
  else await basicNamespaceApi.enable(row.id)
  ElMessage.success(disable ? '已禁用' : '已启用')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  code: '',
  name: '',
  description: '',
  status: true,
})

const rules = {
  code: [
    { required: true, message: '请输入命名空间编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{1,31}$/, message: '以小写字母开头，2-32位（小写字母/数字/-/_）', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入命名空间名称', trigger: 'blur' }],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, {
    code: '',
    name: '',
    description: '',
    status: true,
  })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    code: row.code,
    name: row.name,
    description: row.description || '',
    status: row.status === 1,
  })
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        ...form,
        description: form.description || undefined,
        status: form.status ? 1 : 0,
      }
      if (currentRow.value) {
        await basicNamespaceApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicNamespaceApi.create(payload)
        ElMessage.success('创建成功')
      }
      drawerOpen.value = false
      loadData()
    } finally {
      saving.value = false
    }
  })
}

onMounted(loadData)
</script>

<template>
  <div class="pro-page">
    <div class="pro-card">
      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="命名空间编码">
          <el-input
            v-model="query.code"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="命名空间名称">
          <el-input
            v-model="query.name"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作区 -->
      <div class="pro-toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
      </div>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table v-loading="loading" :data="rows" row-key="id" stripe border>
          <el-table-column label="命名空间编码" min-width="140" align="center">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="命名空间名称"
            min-width="160"
            align="center"
            show-overflow-tooltip
          />
          <el-table-column
            prop="description"
            label="描述"
            min-width="180"
            align="center"
            show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'primary' : 'danger'" size="small" effect="light">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right" align="center" class-name="col-center">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link type="primary" class="link-op" @click="changeStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-link>
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

    <!-- 新增/编辑抽屉 -->
    <el-drawer
      v-model="drawerOpen"
      :title="currentRow ? '编辑命名空间' : '新增命名空间'"
      size="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="命名空间编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="!!currentRow"
            placeholder="例如：default"
          />
        </el-form-item>
        <el-form-item label="命名空间名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：默认租户" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="pro-drawer-footer">
          <el-button @click="drawerOpen = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>
