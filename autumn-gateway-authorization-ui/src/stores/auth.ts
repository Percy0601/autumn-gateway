import { defineStore } from 'pinia'
import { ref } from 'vue'
import { captchaUrl, fetchCsrf, login, type CsrfToken, type LoginResponse } from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const csrf = ref<CsrfToken | null>(null)
  const captchaSrc = ref(captchaUrl())
  const loading = ref(false)
  const errorMessage = ref('')
  const locked = ref(false)
  const remainingAttempts = ref<number | null>(null)
  const maxAttempts = ref(10)

  async function ensureCsrf(force = false) {
    if (!csrf.value || force) {
      csrf.value = await fetchCsrf()
    }
  }

  function refreshCaptcha() {
    captchaSrc.value = captchaUrl()
  }

  async function submit(username: string, password: string, captcha: string) {
    loading.value = true
    errorMessage.value = ''
    try {
      await ensureCsrf(true)
      if (!csrf.value) {
        errorMessage.value = '会话初始化失败，请刷新页面重试'
        return
      }
      const result: LoginResponse = await login({
        username,
        password,
        captcha,
        csrf: { parameterName: csrf.value.parameterName, token: csrf.value.token },
      })

      if (result.success) {
        locked.value = false
        remainingAttempts.value = null
        window.location.href = result.redirect || '/'
        return
      }

      errorMessage.value = result.message || '登录失败'
      locked.value = Boolean(result.locked)
      remainingAttempts.value = result.remainingAttempts ?? null
      if (result.maxAttempts) maxAttempts.value = result.maxAttempts
      // 无论成功失败验证码都已失效
      refreshCaptcha()
    } catch (e) {
      errorMessage.value = '网络异常，请稍后重试'
      refreshCaptcha()
    } finally {
      loading.value = false
    }
  }

  return {
    captchaSrc,
    loading,
    errorMessage,
    locked,
    remainingAttempts,
    maxAttempts,
    ensureCsrf,
    refreshCaptcha,
    submit,
  }
})
