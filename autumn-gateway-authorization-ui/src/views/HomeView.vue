<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-#f5f7fb px-6">
    <div class="w-full max-w-md rounded-2xl bg-white/90 backdrop-blur px-8 py-9 text-center shadow-xl">
      <div class="mx-auto mb-4 w-14 h-14 rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 flex items-center justify-center shadow-lg">
        <svg viewBox="0 0 24 24" class="w-7 h-7 text-white" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <h1 class="text-lg font-semibold text-gray-800">登录成功</h1>
      <p class="mt-2 text-13px text-gray-400">
        当前会话已通过认证。若你是从业务系统跳转而来，请返回原页面继续操作。
      </p>
      <button
        class="mt-6 h-10 px-6 rounded-xl text-14px font-medium text-white bg-gradient-to-r from-brand-500 to-brand-600 hover:from-brand-600 hover:to-brand-700 shadow-lg shadow-brand-500/25"
        @click="router.push('/login')"
      >
        前往登录页
      </button>
    </div>
  </div>
</template>
