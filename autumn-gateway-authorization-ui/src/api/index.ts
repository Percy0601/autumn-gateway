export interface CsrfToken {
  headerName: string
  parameterName: string
  token: string
}

export interface LoginResponse {
  success: boolean
  code?: string
  message?: string
  redirect?: string
  locked?: boolean
  remainingAttempts?: number
  maxAttempts?: number
}

/** 验证码图片地址：带时间戳避免浏览器缓存 */
export const captchaUrl = () => `/api/captcha?t=${Date.now()}`

export async function fetchCsrf(): Promise<CsrfToken | null> {
  const res = await fetch('/api/csrf', { credentials: 'include' })
  if (!res.ok) return null
  return (await res.json()) as CsrfToken
}

export async function login(payload: {
  username: string
  password: string
  captcha: string
  csrf: { parameterName: string; token: string }
}): Promise<LoginResponse> {
  const body = new URLSearchParams()
  body.set('username', payload.username)
  body.set('password', payload.password)
  body.set('captcha', payload.captcha)
  body.set(payload.csrf.parameterName, payload.csrf.token)

  const res = await fetch('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    credentials: 'include',
    body: body.toString(),
  })
  return (await res.json()) as LoginResponse
}
