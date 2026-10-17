<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import http from '../../../utils/request'
import { fmtDateTime, PERM_TYPE_MAP } from '../../../utils/format'

const route = useRoute()
const router = useRouter()
const permId = route.params.id

const loading = ref(false)
const permission = ref(null)
const appMap = ref({})

async function loadData() {
  loading.value = true
  try {
    const [permRes, appsRes] = await Promise.all([
      http.get(`/api/system/permission/${permId}`),
      http.get('/api/system/app/list'),
    ])
    permission.value = permRes.data
    const map = {}
    ;(appsRes.data || []).forEach((app) => {
      map[app.id] = app.name
    })
    appMap.value = map
  } catch (e) {
    ElMessage.error('加载权限数据失败')
  } finally {
    loading.value = false
  }
}

function back() {
  router.push('/system/permission')
}

onMounted(loadData)
</script>

<template>
  <div class="pro-page">
    <div class="page-header">
      <div class="page-header-title">
        权限详情 - {{ permission?.name || '加载中...' }}
      </div>
      <div>
        <el-button :icon="ArrowLeft" @click="back">返回列表</el-button>
      </div>
    </div>

    <div class="desc-box" v-loading="loading">
      <el-descriptions v-if="permission" :column="2" border>
        <el-descriptions-item label="ID">{{ permission.id }}</el-descriptions-item>
        <el-descriptions-item label="权限编码">{{ permission.code }}</el-descriptions-item>
        <el-descriptions-item label="权限名称">{{ permission.name }}</el-descriptions-item>
        <el-descriptions-item label="所属应用">
          {{ appMap[permission.appId] || `应用#${permission.appId}` }}
        </el-descriptions-item>
        <el-descriptions-item label="权限类型">
          <el-tag size="small" effect="plain">
            {{ PERM_TYPE_MAP[permission.permType] || permission.permType }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分类">{{ permission.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源路径 (URL)">
          <code v-if="permission.resourcePath" class="path-code">{{ permission.resourcePath }}</code>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP方法">
          <el-tag size="small" type="success">{{ permission.httpMethod || 'ALL' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="匹配方式">
          {{ permission.matchType || 'exact' }}
        </el-descriptions-item>
        <el-descriptions-item label="父级ID (菜单树)">
          {{ permission.parentId || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="图标">{{ permission.icon || '-' }}</el-descriptions-item>
        <el-descriptions-item label="排序">{{ permission.sort || 0 }}</el-descriptions-item>
        <el-descriptions-item label="菜单隐藏">
          {{ permission.hidden ? '是' : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          {{ permission.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="permission.status === 1 ? 'success' : 'danger'" size="small">
            {{ permission.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtDateTime(permission.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fmtDateTime(permission.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading" description="暂无数据" />
    </div>
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

.path-code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
