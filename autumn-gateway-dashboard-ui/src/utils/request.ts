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

const http = axios.create({
	baseURL,
	timeout: 15000,
	// 统一认证后浏览器不再持有令牌：身份靠网关下发的会话 Cookie。
	// 同源场景默认就会带 Cookie，这里显式声明是为了将来跨域部署（网关与页面不同域）时不会漏掉。
	withCredentials: true
})

// 请求拦截：不再注入 Authorization 头。
//
// 旧实现从 localStorage 取 JWT 塞进请求头 —— 令牌由 dashboard 自签（HS256）。
// 现在令牌统一由 autumn-gateway-authorization 签发（RS256），并且只存在于网关的服务端会话里：
//  浏览器 ──(SESSION Cookie)──> 网关 ──(TokenRelay 注入 Bearer)──> dashboard
// 前端拿不到令牌，也不需要拿：所有请求经网关，网关自己会补 Authorization 头。

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
			// 会话已失效：不在这里跳转，交给路由守卫统一处理
			// （守卫会把浏览器送到网关的 OAuth2 登录入口）。
			// 因为授权服务器侧通常还保留着会话，用户往往是无感知地自动重新登录。
			ElMessage.warning('登录已过期，正在重新认证')
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
