<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const username = ref('')
const password = ref('')
const captcha = ref('')
const captchaInput = ref<HTMLInputElement | null>(null)
const lockCountdown = ref(0)
let timer: number | undefined

const startCountdown = (minutes: number) => {
  lockCountdown.value = Math.max(1, minutes) * 60
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    lockCountdown.value -= 1
    if (lockCountdown.value <= 0) {
      window.clearInterval(timer)
      auth.locked = false
    }
  }, 1000)
}

const onSubmit = async () => {
  if (!username.value || !password.value || !captcha.value) {
    auth.errorMessage = '请填写用户名、密码和验证码'
    return
  }
  await auth.submit(username.value, password.value, captcha.value)
  captcha.value = ''
  if (auth.locked) startCountdown(1)
}

const loginWithWeChat = () => {
  window.location.href = '/login/wechat'
}

onMounted(() => {
  auth.ensureCsrf()
  auth.loadProviders()
  auth.refreshCaptcha()
})
onUnmounted(() => window.clearInterval(timer))

const countdownText = () => {
  const m = Math.floor(lockCountdown.value / 60)
  const s = lockCountdown.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
}
</script>

<template>
  <div class="min-h-screen w-full flex items-center justify-center relative overflow-hidden bg-#f5f7fb">
    <!-- 背景装饰 -->
    <div class="absolute -top-32 -left-24 w-96 h-96 rounded-full bg-brand-400 opacity-30 blur-3xl"></div>
    <div class="absolute -bottom-40 -right-20 w-120 h-120 rounded-full bg-brand-600 opacity-20 blur-3xl"></div>

    <div class="relative w-full max-w-105 px-6">
      <div class="bg-white/90 backdrop-blur rounded-2xl shadow-xl px-8 py-9">
        <!-- 标题 -->
        <div class="mb-8 text-center">
          <div class="mx-auto mb-4 w-14 h-14 rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 flex items-center justify-center shadow-lg">
            <svg viewBox="0 0 24 24" class="w-7 h-7 text-white" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M12 3l7 3v6c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6l7-3z" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M9 12.5l2 2 4-4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <h1 class="text-xl font-semibold text-gray-800 tracking-wide">统一认证中心</h1>
          <p class="mt-1 text-13px text-gray-400">Autumn Gateway Authorization</p>
        </div>

        <!-- 错误提示 -->
        <div
          v-if="auth.errorMessage"
          class="mb-5 flex items-start gap-2 rounded-xl border px-3 py-2.5 text-13px"
          :class="auth.locked ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-red-200 bg-red-50 text-red-600'"
        >
          <svg viewBox="0 0 24 24" class="w-4 h-4 mt-0.5 flex-none" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 8v5M12 16.5v.5" stroke-linecap="round" />
          </svg>
          <div class="flex-1">
            <div>{{ auth.errorMessage }}</div>
            <div v-if="auth.locked && lockCountdown > 0" class="mt-1 font-medium">
              解锁倒计时 {{ countdownText() }}
            </div>
            <div v-else-if="auth.remainingAttempts !== null" class="mt-1">
              还可尝试 {{ auth.remainingAttempts }} 次（共 {{ auth.maxAttempts }} 次）
            </div>
          </div>
        </div>

        <form @submit.prevent="onSubmit" class="space-y-4">
          <!-- 用户名 -->
          <div>
            <label class="ag-label">用户名</label>
            <div class="relative">
              <svg viewBox="0 0 24 24" class="absolute left-3 top-1/2 -translate-y-1/2 w-4.5 h-4.5 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="12" cy="8" r="3.5" />
                <path d="M5 20c1.5-3.5 4-5 7-5s5.5 1.5 7 5" stroke-linecap="round" />
              </svg>
              <input
                v-model="username"
                type="text"
                autocomplete="username"
                placeholder="请输入用户名"
                class="ag-input ag-input--with-icon"
              />
            </div>
          </div>

          <!-- 密码 -->
          <div>
            <label class="ag-label">密码</label>
            <div class="relative">
              <svg viewBox="0 0 24 24" class="absolute left-3 top-1/2 -translate-y-1/2 w-4.5 h-4.5 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="4.5" y="10" width="15" height="9.5" rx="2.2" />
                <path d="M8 10V7.5a4 4 0 018 0V10" stroke-linecap="round" />
              </svg>
              <input
                v-model="password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
                class="ag-input ag-input--with-icon"
              />
            </div>
          </div>

          <!-- 验证码 -->
          <div>
            <label class="ag-label">验证码</label>
            <div class="ag-captcha-row">
              <input
                ref="captchaInput"
                v-model="captcha"
                type="text"
                maxlength="6"
                placeholder="请输入验证码"
                class="ag-input ag-captcha-input"
              />
              <img
                :src="auth.captchaSrc"
                alt="验证码"
                title="点击刷新"
                @click="auth.refreshCaptcha()"
                class="ag-captcha-img"
              />
            </div>
          </div>

          <button
            type="submit"
            :disabled="auth.loading || auth.locked"
            class="mt-2 w-full h-11 rounded-xl text-15px font-medium text-white shadow-lg shadow-brand-500/25 transition bg-gradient-to-r from-brand-500 to-brand-600 hover:from-brand-600 hover:to-brand-700 disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            <svg v-if="auth.loading" viewBox="0 0 24 24" class="w-4.5 h-4.5 animate-spin" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M12 3a9 9 0 019 9" stroke-linecap="round" />
            </svg>
            <span>{{ auth.loading ? '登录中…' : auth.locked ? '账号已锁定' : '登 录' }}</span>
          </button>

          <!-- 微信登录（仅后端 autumn.wechat.enabled=true 时显示） -->
          <div v-if="auth.wechatEnabled" class="mt-4">
            <div class="flex items-center gap-3">
              <div class="flex-1 h-px bg-gray-100"></div>
              <span class="text-12px text-gray-400">其他登录方式</span>
              <div class="flex-1 h-px bg-gray-100"></div>
            </div>
            <button
              type="button"
              @click="loginWithWeChat"
              class="mt-4 w-full h-11 rounded-xl text-15px font-medium text-white shadow-lg shadow-[#07c160]/25 transition bg-[#07c160] hover:bg-[#06ad56] flex items-center justify-center gap-2"
            >
              <svg viewBox="0 0 24 24" class="w-5 h-5" fill="currentColor">
                <path d="M9.1 3.5C5.2 3.5 2 6.2 2 9.5c0 1.9 1.1 3.6 2.8 4.8l-.7 2.1 2.4-1.2c.9.2 1.8.4 2.6.4h.7a5.6 5.6 0 01-.2-1.5c0-3.3 3.1-5.9 6.9-5.9h.7C14.5 5.5 12 3.5 9.1 3.5z" />
                <path d="M22 13.8c0-2.8-2.7-5.1-6-5.1s-6 2.3-6 5.1 2.7 5.1 6 5.1c.8 0 1.5-.1 2.2-.3l2 .9-.6-1.6c1.5-1 2.4-2.5 2.4-3.9z" />
              </svg>
              <span>微信扫码登录</span>
            </button>
          </div>
        </form>

        <p class="mt-6 text-center text-12px text-gray-400">
          连续密码错误 {{ auth.maxAttempts }} 次将锁定账号，请妥善保管密码
        </p>
      </div>
    </div>
  </div>
</template>
