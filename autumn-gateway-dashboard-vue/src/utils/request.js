import axios from 'axios'
import { ElMessage } from 'element-plus'

// API 前缀推导，保证「独立部署」和「挂在网关子路径下」两种场景都能正确拼接：
//   独立部署：页面位于 http://host/          -> baseURL = ''          -> /api/...
//   网关路由：页面位于 http://host/dashboard/ -> baseURL = '/dashboard' -> /dashboard/api/...
// 也可用 VITE_API_BASE 显式覆盖。
const resolveApiBase = () => {
  if (import.meta.env.VITE_API_BASE) return import.meta.env.VITE_API_BASE
  if (typeof document === 'undefined') return ''
  const base = new URL(import.meta.env.BASE_URL || './', document.baseURI)
  return base.pathname.replace(/\/+$/, '')
}

// 开发环境走 vite 代理；正式环境同源（静态资源由 dashboard 服务本身提供）
const baseURL = resolveApiBase()

const http = axios.create({
  baseURL,
  timeout: 15000,
})

// 请求拦截：附加 JWT
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers = { ...config.headers, Authorization: `Bearer ${token}` }
  }
  return config
})

// 响应拦截：统一处理后端 Result 结构 + 401
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 兼容两种返回：
    //  业务接口: { code:200, message, data, total }
    //  login/currentUser: { status } / { success, data }
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      if (!location.hash.includes('/login')) {
        location.hash = '#/login'
      }
      ElMessage.warning('登录已过期，请重新登录')
    } else {
      const msg =
        error.response?.data?.message ||
        error.response?.data?.errorMessage ||
        error.message ||
        '网络错误，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

export default http
