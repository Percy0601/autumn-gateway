<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import {
  copyText,
  fmtDateTime,
  PERM_TYPE_MAP,
} from '../../../utils/format'

const route = useRoute()
const router = useRouter()
const roleId = route.params.id

const tab = ref(String(route.query.tab || 'basic'))
const loading = ref(false)
const role = ref(null)
const appMap = ref({})

// 权限相关
const allPermissions = ref([])
const selectedPermissionIds = ref([])

// 用户相关
const allUsers = ref([])
const selectedUserIds = ref([])

async function loadRoleData() {
  loading.value = true
  try {
    const [roleRes, appsRes] = await Promise.all([
      http.get(`/api/system/role/${roleId}`),
      http.get('/api/system/app/list'),
    ])
    role.value = roleRes.data
    const map = {}
    ;(appsRes.data || []).forEach((app) => {
      map[app.id] = app.name
    })
    appMap.value = map

    try {
      const [permsRes, rolePermsRes, usersRes, roleUsersRes] = await Promise.all([
        http.get('/api/system/permission', { params: { pageSize: 9999 } }),
        http.get(`/api/system/role/${roleId}/permissions`),
        http.get('/api/system/user', { params: { pageSize: 9999 } }),
        http.get(`/api/system/role/${roleId}/users`),
      ])
      allPermissions.value = permsRes.data || []
      selectedPermissionIds.value = (rolePermsRes.data || []).map((item) => item.id)
      allUsers.value = usersRes.data || []
      selectedUserIds.value = (roleUsersRes.data || []).map((item) => item.id)
    } catch (e) {
      ElMessage.warning('部分关联数据加载失败，请刷新页面重试')
    }
  } catch (e) {
    ElMessage.error('加载角色数据失败')
  } finally {
    loading.value = false
  }
}

// 关联表数据源
const permDataSource = computed(() =>
  allPermissions.value.map((perm) => ({
    ...perm,
    _associated: selectedPermissionIds.value.includes(perm.id),
  })),
)
const userDataSource = computed(() =>
  allUsers.value.map((user) => ({
    ...user,
    _associated: selectedUserIds.value.includes(user.id),
  })),
)

async function togglePermission(row) {
  const currentlyAssociated = selectedPermissionIds.value.includes(row.id)
  const newIds = currentlyAssociated
    ? selectedPermissionIds.value.filter((id) => id !== row.id)
    : [...selectedPermissionIds.value, row.id]
  try {
    await http.put(`/api/system/role/${roleId}/permissions`, newIds)
    selectedPermissionIds.value = newIds
    ElMessage.success(currentlyAssociated ? '已解绑权限' : '已关联权限')
  } catch {
    /* 拦截器已提示 */
  }
}

async function toggleUser(row) {
  const currentlyAssociated = selectedUserIds.value.includes(row.id)
  const newIds = currentlyAssociated
    ? selectedUserIds.value.filter((id) => id !== row.id)
    : [...selectedUserIds.value, row.id]
  try {
    await http.put(`/api/system/role/${roleId}/users`, newIds)
    selectedUserIds.value = newIds
    ElMessage.success(currentlyAssociated ? '已解绑用户' : '已关联用户')
  } catch {
    /* 拦截器已提示 */
  }
}

function back() {
  router.push('/system/role')
}

onMounted(loadRoleData)
</script>

<template>
  <div class="pro-page">
    <!-- 页头（仿 antd PageContainer） -->
    <div class="page-header">
      <div class="page-header-title">
        角色详情 - {{ role?.name || '加载中...' }}
      </div>
      <div>
        <el-button :icon="ArrowLeft" @click="back">返回列表</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <div class="desc-box" v-loading="loading">
          <el-descriptions v-if="role" :column="2" border>
            <el-descriptions-item label="ID">{{ role.id }}</el-descriptions-item>
            <el-descriptions-item label="角色编码">{{ role.code }}</el-descriptions-item>
            <el-descriptions-item label="角色名称">{{ role.name }}</el-descriptions-item>
            <el-descriptions-item label="所属应用">
              {{ appMap[role.appId] || `应用#${role.appId}` }}
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              {{ role.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="role.status === 1 ? 'success' : 'danger'" size="small">
                {{ role.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmtDateTime(role.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtDateTime(role.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else-if="!loading" description="暂无数据" />
        </div>
      </el-tab-pane>

      <!-- 关联权限 -->
      <el-tab-pane :label="`关联权限（已关联 ${selectedPermissionIds.length} 项）`" name="permissions">
        <div class="pro-card" style="margin-top: 12px">
          <div class="pro-table-wrap">
            <el-table
              v-loading="loading"
              :data="permDataSource"
              row-key="id"
              stripe
            >
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column label="权限编码" min-width="150">
                <template #default="{ row }">
                  <el-tooltip content="点击复制" placement="top">
                    <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="权限名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="所属应用" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ appMap[row.appId] || '-' }}</template>
              </el-table-column>
              <el-table-column label="权限类型" width="90">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">
                    {{ PERM_TYPE_MAP[row.permType] || row.permType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="资源路径" min-width="180">
                <template #default="{ row }">
                  <code v-if="row.resourcePath">{{ row.resourcePath }}</code>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                    {{ row.status === 1 ? '正常' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row._associated ? 'success' : 'info'" size="small" effect="plain">
                    {{ row._associated ? '已关联' : '未关联' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-link
                    :type="row._associated ? 'danger' : 'primary'"
                    @click="togglePermission(row)"
                  >
                    {{ row._associated ? '解绑' : '关联' }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- 关联用户 -->
      <el-tab-pane :label="`关联用户（已关联 ${selectedUserIds.length} 项）`" name="users">
        <div class="pro-card" style="margin-top: 12px">
          <div class="pro-table-wrap">
            <el-table
              v-loading="loading"
              :data="userDataSource"
              row-key="id"
              stripe
            >
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column label="用户名" min-width="130">
                <template #default="{ row }">
                  <el-tooltip content="点击复制" placement="top">
                    <span class="copy-text" @click="copyText(row.username)">{{ row.username }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.nickname || '-' }}</template>
              </el-table-column>
              <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.email || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                    {{ row.status === 1 ? '正常' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row._associated ? 'success' : 'info'" size="small" effect="plain">
                    {{ row._associated ? '已关联' : '未关联' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-link
                    :type="row._associated ? 'danger' : 'primary'"
                    @click="toggleUser(row)"
                  >
                    {{ row._associated ? '解绑' : '关联' }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 16px 24px;
  margin-bottom: 16px;
  border-radius: 8px;
}

.page-header-title {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.code-cell code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
