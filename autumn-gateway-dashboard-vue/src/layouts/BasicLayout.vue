<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CaretBottom,
  Expand,
  Fold,
  Setting,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

const activeMenu = computed(() => route.meta.activeMenu || route.path)

const pageTitle = computed(() => route.meta.title || '')

const menus = [
  {
    index: '/system',
    title: '系统管理',
    icon: Setting,
    children: [
      { index: '/system/app', title: '应用管理' },
      { index: '/system/role', title: '角色管理' },
      { index: '/system/user', title: '用户管理' },
      { index: '/system/permission', title: '权限管理' },
    ],
  },
]

function goHome() {
  router.push('/system/app')
}

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

onMounted(() => {
  if (!userStore.currentUser) {
    userStore.fetchUserInfo()
  }
})
</script>

<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sider" :class="{ collapsed }">
      <div class="sider-logo" @click="goHome">
        <img src="/logo.svg" alt="logo" class="sider-logo-img" />
        <span v-show="!collapsed" class="sider-logo-title">Ant Design Pro</span>
      </div>
      <el-scrollbar class="sider-menu-scroll">
        <el-menu
          class="sider-menu"
          :default-active="activeMenu"
          :collapse="collapsed"
          :collapse-transition="false"
          router
          background-color="#fff"
          text-color="rgba(0,0,0,0.88)"
          active-text-color="#1677ff"
        >
          <template v-for="menu in menus" :key="menu.index">
            <el-sub-menu v-if="menu.children" :index="menu.index">
              <template #title>
                <el-icon><component :is="menu.icon" /></el-icon>
                <span>{{ menu.title }}</span>
              </template>
              <el-menu-item v-for="child in menu.children" :key="child.index" :index="child.index">
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.index">
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <!-- 主区域 -->
    <div class="main-wrap">
      <header class="header">
        <div class="header-left">
          <el-icon class="trigger" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>系统管理</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" :src="userStore.currentUser?.avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="user-name">{{ userStore.displayName }}</span>
              <el-icon class="caret"><CaretBottom /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sider {
  width: 208px;
  background: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f0f0f0;
  flex-shrink: 0;
  transition: width 0.2s;
  box-shadow: 2px 0 8px 0 rgba(29, 35, 41, 0.05);
}

.sider.collapsed {
  width: 64px;
}

.sider-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  cursor: pointer;
  flex-shrink: 0;
  overflow: hidden;
}

.sider-logo-img {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
}

.sider-logo-title {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  white-space: nowrap;
}

.sider-menu-scroll {
  flex: 1;
  overflow: hidden;
}

.sider-menu {
  border-right: none;
}

.sider-menu:not(.el-menu--collapse) {
  width: 208px;
}

.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.trigger {
  font-size: 18px;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.45);
}

.trigger:hover {
  color: rgba(0, 0, 0, 0.88);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: rgba(0, 0, 0, 0.88);
}

.user-name {
  font-size: 14px;
}

.caret {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.content {
  flex: 1;
  overflow: auto;
  background: #f0f2f5;
}
</style>
