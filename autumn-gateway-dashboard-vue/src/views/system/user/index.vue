<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { basicUserApi } from '../../../api/basic'
import { copyText, fmtDateTime } from '../../../utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({
  current: 1,
  pageSize: 10,
  empNo: '',
})

async function loadData() {
  loading.value = true
  try {
    const body = await basicUserApi.list(query)
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
  query.empNo = ''
  handleSearch()
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除员工 ${row.empNo} 吗？`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await basicUserApi.remove(row.id)
  ElMessage.success('已删除')
  loadData()
}

// ---------- 抽屉表单 ----------
const drawerOpen = ref(false)
const saving = ref(false)
const currentRow = ref(null)
const formRef = ref()
const form = reactive({
  empNo: '',
  admin: false,
  latestRoleId: '',
})

const rules = {
  empNo: [
    { required: true, message: '请输入员工工号', trigger: 'blur' },
    { min: 3, max: 50, message: '长度 3-50 位', trigger: 'blur' },
  ],
}

function openCreate() {
  currentRow.value = null
  Object.assign(form, { empNo: '', admin: false, latestRoleId: '' })
  drawerOpen.value = true
}

function openEdit(row) {
  currentRow.value = row
  Object.assign(form, {
    empNo: row.empNo,
    admin: row.admin === 1,
    latestRoleId: row.latestRoleId || '',
  })
  drawerOpen.value = true
}

function handleSubmit() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        empNo: form.empNo,
        admin: form.admin ? 1 : 0,
        latestRoleId: Number(form.latestRoleId) || 0,
      }
      if (currentRow.value) {
        await basicUserApi.update(currentRow.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await basicUserApi.create(payload)
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
      <div class="pro-card-head">
        <div class="pro-card-title">用户列表</div>
        <div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
        </div>
      </div>

      <!-- 搜索 -->
      <el-form inline class="pro-search" @submit.prevent>
        <el-form-item label="员工工号">
          <el-input
            v-model="query.empNo"
            placeholder="请输入"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <div class="pro-table-wrap">
        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="员工工号" min-width="140">
            <template #default="{ row }">
              <el-tooltip content="点击复制" placement="top">
                <span class="copy-text" @click="copyText(row.empNo)">{{ row.empNo }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="是否管理员" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.admin === 1 ? 'danger' : 'info'" size="small">
                {{ row.admin === 1 ? '管理员' : '普通' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近登录角色" min-width="130">
            <template #default="{ row }">
              {{ row.latestRoleId ? `角色#${row.latestRoleId}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ fmtDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-link type="primary" class="link-op" @click="openEdit(row)">编辑</el-link>
              <el-link type="danger" class="link-op" @click="handleRemove(row)">删除</el-link>
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
      :title="currentRow ? '编辑用户' : '新增用户'"
      size="420px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="员工工号" prop="empNo">
          <el-input
            v-model="form.empNo"
            :disabled="!!currentRow"
            placeholder="例如：E10086"
          />
        </el-form-item>
        <el-form-item label="是否管理员">
          <el-switch v-model="form.admin" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="最近登录角色">
          <el-input-number v-model="form.latestRoleId" :min="0" :precision="0" style="width: 100%" />
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
