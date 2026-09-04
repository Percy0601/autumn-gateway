<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, Lock, User } from '@element-plus/icons-vue'
import http from '../../utils/request'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const loginError = ref(false)
const autoLogin = ref(true)

const form = reactive({
  username: 'admin',
  password: '123456',
})

const rules = {
  username: [{ required: true, message: '请输入用户名!', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码！', trigger: 'blur' }],
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    loginError.value = false
    try {
      const body = await http.post('/api/login/account', {
        ...form,
        type: 'account',
      })
      if (body && body.status === 'ok') {
        if (body.token) {
          userStore.setToken(body.token)
        }
        ElMessage.success('登录成功！')
        await userStore.fetchUserInfo()
        const redirect = route.query.redirect
        const target =
          typeof redirect === 'string' && redirect.startsWith('/')
            ? redirect
            : '/system/app'
        router.replace(target)
        return
      }
      // 登录失败：与 React 版展示一致
      loginError.value = true
    } catch {
      ElMessage.error('登录失败，请重试！')
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <!-- 左侧品牌区 -->
    <div class="login-left">
      <div class="login-left-inner">
        <div class="login-slogan">Ant Design Pro</div>
        <div class="login-sub-slogan">开箱即用的中后台前端/设计解决方案</div>
        <div class="login-left-desc">
          由网关管理后台 Vue 版驱动，涵盖应用、用户、角色与权限/资源管理能力。
        </div>
        <div class="login-footer">Ant Design ©2026 Created by Ant UED</div>
      </div>
    </div>

    <!-- 右侧登录卡片 -->
    <div class="login-right">
      <div class="login-card">
        <div class="login-card-head">
          <img src="/logo.svg" alt="logo" class="login-logo" />
          <div class="login-title">Ant Design Pro</div>
          <div class="login-subtitle">Autumn Gateway Dashboard</div>
        </div>

        <div v-if="loginError" class="login-alert">
          <el-icon class="login-alert-icon"><CircleCloseFilled /></el-icon>
          账户或密码错误(admin/ant.design)
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名: admin"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码: ant.design"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <div class="login-options">
              <el-checkbox v-model="autoLogin">自动登录</el-checkbox>
              <a class="forgot-link" href="javascript:void(0)">忘记密码</a>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleSubmit"
            >
              登 录
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: auto;
}

/* 左侧：渐变品牌区（近似 antd pro 登录页风格） */
.login-left {
  flex: 1 1 55%;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.16) 0, transparent 30%),
    radial-gradient(circle at 80% 70%, rgba(255, 255, 255, 0.12) 0, transparent 35%),
    linear-gradient(135deg, #0d47a1 0%, #1565c0 45%, #1e88e5 100%);
  color: #fff;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-left-inner {
  max-width: 460px;
  padding: 40px;
}

.login-slogan {
  font-size: 40px;
  font-weight: 600;
  letter-spacing: 1px;
}

.login-sub-slogan {
  font-size: 18px;
  margin-top: 16px;
  opacity: 0.92;
}

.login-left-desc {
  margin-top: 20px;
  font-size: 14px;
  line-height: 1.8;
  opacity: 0.75;
}

.login-footer {
  position: absolute;
  bottom: 40px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 14px;
  opacity: 0.65;
}

/* 右侧卡片 */
.login-right {
  flex: 1 1 45%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 40px;
}

.login-card-head {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  width: 44px;
  height: 44px;
}

.login-title {
  font-size: 33px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  margin-top: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  margin-top: 8px;
}

.login-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid #ffccc7;
  background: #fff2f0;
  color: #ff4d4f;
  font-size: 14px;
  margin-bottom: 24px;
}

.login-alert-icon {
  font-size: 16px;
}

.login-options {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.forgot-link {
  color: #1677ff;
  font-size: 14px;
}

.login-btn {
  width: 100%;
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

@media (max-width: 900px) {
  .login-left {
    display: none;
  }
}
</style>
