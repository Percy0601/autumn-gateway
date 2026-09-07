<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import { copyText, fmtDateTime } from '../../../utils/format'

const route = useRoute()
const router = useRouter()
const userId = route.params.id

const tab = ref('basic')
const loading = ref(false)
const user = ref(null)

// 应用相关
const allApps = ref([])
const selectedAppIds = ref([])

// 角色相关
const allRoles = ref([])
const selectedRoleIds = ref([])
const appMap = ref({})

async function loadUserData() {
  loading.value = true
  try {
    const [userRes, appsRes, userAppsRes, rolesRes, userRolesRes] =
      await Promise.all([
        http.get(`/api/system/user/${userId}`),
        http.get('/api/system/app/list'),
        http.get(`/api/system/user/${userId}/apps`),
        http.get('/api/system/role/list'),
        http.get(`/api/system/user/${userId}/roles`),
      ])
    user.value = userRes.data
    allApps.value = appsRes.data || []
    selectedAppIds.value = (userAppsRes.data || []).map((item) => item.appId)
    allRoles.value = rolesRes.data || []
    selectedRoleIds.value = (userRolesRes.data || []).map((item) => item.roleId)

    const map = {}
    ;(appsRes.data || []).forEach((app) => {
      map[app.id] = app.name
    })
    appMap.value = map
  } catch (e) {
    ElMessage.error('加载用户数据失败')
  } finally {
    loading.value = false
  }
}

// 关联应用表
const appDataSource = computed(() =>
  allApps.value.map((app) => ({
    ...app,
    _associated: selectedAppIds.value.includes(app.id),
  })),
)

// 关联角色表（仅显示已关联应用下的角色）
const roleDataSource = computed(() =>
  allRoles.value
    .filter((role) => selectedAppIds.value.includes(role.appId))
    .map((role) => ({
      ...role,
      _associated: selectedRoleIds.value.includes(role.id),
    })),
)

async function toggleApp(row) {
  const currentlyAssociated = selectedAppIds.value.includes(row.id)
  const newIds = currentlyAssociated
    ? selectedAppIds.value.filter((id) => id !== row.id)
    : [...selectedAppIds.value, row.id]
  try {
    await http.put(`/api/system/user/${userId}/apps`, { appIds: newIds })
    selectedAppIds.value = newIds
    ElMessage.success(currentlyAssociated ? '已解绑应用' : '已关联应用')
  } catch {
    /* 拦截器已提示 */
  }
}

async function toggleRole(row) {
  const currentlyAssociated = selectedRoleIds.value.includes(row.id)
  const newIds = currentlyAssociated
    ? selectedRoleIds.value.filter((id) => id !== row.id)
    : [...selectedRoleIds.value, row.id]
  try {
    await http.put(`/api/system/user/${userId}/roles`, { roleIds: newIds })
    selectedRoleIds.value = newIds
    ElMessage.success(currentlyAssociated ? '已解绑角色' : '已关联角色')
  } catch {
    /* 拦截器已提示 */
  }
}

function back() {
  router.push('/system/user')
}

onMounted(loadUserData)
</script>

<template>
  <div class="pro-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-header-title">
        用户详情 - {{ user?.username || '加载中...' }}
      </div>
      <div>
        <el-button :icon="ArrowLeft" @click="back">返回列表</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <div class="desc-box" v-loading="loading">
          <el-descriptions v-if="user" :column="2" border>
            <el-descriptions-item label="ID">{{ user.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ user.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">
              <span class="nickname-cell">
                <el-avatar :size="22" :src="user.avatar">
                  <el-icon><UserFilled /></el-icon>
                </el-avatar>
                {{ user.nickname || '-' }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ user.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机">{{ user.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="员工号">{{ user.empNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="user.status === 1 ? 'success' : 'danger'" size="small">
                {{ user.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最后登录">{{ fmtDateTime(user.lastLoginAt) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmtDateTime(user.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtDateTime(user.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else-if="!loading" description="暂无数据" />
        </div>
      </el-tab-pane>

      <!-- 关联应用 -->
      <el-tab-pane label="关联应用" name="apps">
        <div class="pro-card" style="margin-top: 12px">
          <div class="pro-table-wrap">
            <el-table v-loading="loading" :data="appDataSource" row-key="id" stripe>
              <el-table-column prop="id" label="应用ID" width="90" />
              <el-table-column prop="name" label="应用名称" min-width="160" show-overflow-tooltip />
              <el-table-column prop="appid" label="应用标识" min-width="130">
                <template #default="{ row }">
                  <el-tooltip content="点击复制" placement="top">
                    <span class="copy-text" @click="copyText(row.appid)">{{ row.appid }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="关联状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row._associated ? 'success' : 'info'" size="small" effect="plain">
                    {{ row._associated ? '已关联' : '未关联' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-link
                    :type="row._associated ? 'danger' : 'primary'"
                    @click="toggleApp(row)"
                  >
                    {{ row._associated ? '解绑' : '关联' }}
                  </el-link>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- 关联角色 -->
      <el-tab-pane label="关联角色" name="roles">
        <div class="pro-card" style="margin-top: 12px">
          <div class="table-hint">角色列表（仅显示已关联应用下的角色）</div>
          <div class="pro-table-wrap">
            <el-table
              v-loading="loading"
              :data="roleDataSource"
              row-key="id"
              stripe
              empty-text="请先关联应用后再分配角色"
            >
              <el-table-column prop="id" label="角色ID" width="90" />
              <el-table-column prop="code" label="角色编码" min-width="140">
                <template #default="{ row }">
                  <el-tooltip content="点击复制" placement="top">
                    <span class="copy-text" @click="copyText(row.code)">{{ row.code }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="角色名称" min-width="150" show-overflow-tooltip />
              <el-table-column label="所属应用" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ appMap[row.appId] || `应用#${row.appId}` }}</template>
              </el-table-column>
              <el-table-column label="关联状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row._associated ? 'success' : 'info'" size="small" effect="plain">
                    {{ row._associated ? '已关联' : '未关联' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-link
                    :type="row._associated ? 'danger' : 'primary'"
                    @click="toggleRole(row)"
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

.nickname-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.table-hint {
  margin-bottom: 12px;
  color: rgba(0, 0, 0, 0.65);
}
</style>
