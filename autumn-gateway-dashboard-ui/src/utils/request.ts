import axios from 'axios'
import { ElMessage } from 'element-plus'

// API 前缀推导：独立部署(base './')或挂在网关子路径下都能正确拼接。
// 也可用 VITE_API_BASE 显式覆盖。
const resolveApiBase = (): string => {
	const env = (import.meta as any).env || {}
	if (env.VITE_API_BASE) return env.VITE_API_BASE
	if (typeof document === 'undefined') return ''
	const base = new URL(env.BASE_URL || './', document.baseURI)
	return base.pathname.replace(/\/+$/, '')
}

const baseURL = resolveApiBase()

// 无状态 JWT：身份只靠 Authorization 头传递，不用 Cookie 会话（故不开 withCredentials）。
const http = axios.create({
	baseURL,
	timeout: 15000
})

// 请求拦截：附加 JWT（唯一凭据传递方式）。
//
// token 由网关 starter 的 POST /gateway/endpoint/login 签发，登录页存进 localStorage。
// 这里每次实时读 localStorage 而不是缓存一份 —— 401 拦截器会清掉它，
// 缓存副本会让「已过期」的请求继续带着旧 token 重试。
http.interceptors.request.use((config: any) => {
	const token = localStorage.getItem('token')
	if (token) {
		config.headers = { ...config.headers, Authorization: `Bearer ${token}` }
	}
	return config
})

// 响应拦截：统一处理后端 Result 结构 + 401
http.interceptors.response.use(
	(response: any) => {
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
	(error: any) => {
		if (error.response && error.response.status === 401) {
			// 只清本地 token + 提示，**跳转统一交给路由守卫处理**：
			// 1) 路由是 createWebHistory（无 hash 模式），改 hash 不会触发 vue-router；
			// 2) 这里再强制跳转会与守卫形成双重跳转。
			localStorage.removeItem('token')
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
	}
)

export const service = http
export default http
