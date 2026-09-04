import axios from 'axios'
import { ElMessage } from 'element-plus'

// 开发环境走 vite 代理；也可通过 VITE_API_BASE 覆盖
const baseURL = import.meta.env.VITE_API_BASE || ''

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
