import { defineStore } from 'pinia'
import http from '@/utils/request'

/**
 * 统一认证后的登录 / 登出入口（相对路径，经网关访问时直接生效）。
 * 后端 /api/currentUser 在 401 时会把 loginUrl 带回来，以服务端为准。
 */
const DEFAULT_LOGIN_URL = '/oauth2/authorization/web-client'
const DEFAULT_LOGOUT_URL = '/logout'

export const useUserStore = defineStore('userStore', {
	state: () => ({
		// 用户信息（结构由后端 /api/currentUser 决定）
		user: {
			id: '',
			username: '',
			name: '',
			// 部分历史接口/库数据用 realName，后端 /api/currentUser 目前只给 name
			realName: '',
			avatar: ''
		},
		currentUser: null as any,
		/**
		 * 是否已认证。
		 *
		 * 统一认证落地后，前端不再持有令牌 —— 令牌只存在于网关的服务端会话里，
		 * 浏览器侧只有一枚 HttpOnly 的 SESSION Cookie，JS 读不到也写不了。
		 * 所以「是否登录」不能再看本地有没有 token，只能问后端「我是谁」。
		 */
		authenticated: false,
		// 按钮级权限标识清单（后端 /api/currentUser 的 authorityList），供 v-auth 指令使用。
		// 超管的清单里带一个 "*"，v-auth 见 "*" 即视为全放行。
		authorityList: [] as string[],
		// 是否超级管理员（后端 /api/currentUser 的 superAdmin）
		superAdmin: false,
		// 登录 / 登出入口
		loginUrl: DEFAULT_LOGIN_URL,
		logoutUrl: DEFAULT_LOGOUT_URL
	}),
	getters: {
		isLogin: (state) => state.authenticated,
		// 显示名：realName 与 name 兼容取值，避免首页问候语渲染成「早安，，」
		displayName: (state) =>
			state.currentUser?.realName ||
			state.user?.realName ||
			state.currentUser?.name ||
			state.user?.name ||
			state.user?.username ||
			'未登录'
	},
	actions: {
		/** 跳转到统一认证入口（网关 → 授权服务器登录页） */
		redirectToLogin() {
			window.location.href = this.loginUrl || DEFAULT_LOGIN_URL
		},

		/** 清除本地认证态（不跳转） */
		markUnauthenticated() {
			this.authenticated = false
			this.currentUser = null
			this.authorityList = []
			this.superAdmin = false
		},

		/**
		 * 确认当前会话是否已认证。
		 *
		 * <p>已认证时直接复用状态，不再打接口；未认证时问一次 {@code /api/currentUser}。</p>
		 *
		 * <p>网关会在请求进来前把 access_token 注入转发头，所以前端什么都不用带，
		 * 只要带得上会话 Cookie 即可（axios 已配 withCredentials）。</p>
		 */
		async ensureAuthenticated(): Promise<boolean> {
			if (this.authenticated) {
				return true
			}
			try {
				const body: any = await http.get('/api/currentUser')
				if (!body || !body.data) {
					this.markUnauthenticated()
					return false
				}
				if (body.data.loginUrl) {
					this.loginUrl = body.data.loginUrl
				}
				this.currentUser = body.data
				this.user = { ...this.user, ...body.data, id: body.data.userid ?? body.data.id ?? this.user.id }
				// 权限清单提到 store 顶层：v-auth 指令读的是 store.authorityList，
				// 而后端下发在 data.authorityList 里，不搬上来按钮权限永远不会生效
				this.authorityList = Array.isArray(body.data.authorityList) ? body.data.authorityList : []
				this.superAdmin = !!body.data.superAdmin
				this.authenticated = true
				return true
			} catch (error) {
				// 401/403 是明确的未认证；网络抖动也按未认证处理 ——
				// 反正授权服务器侧通常还留着会话，跳过去会无感知地自动重新登录，不会误伤用户。
				this.markUnauthenticated()
				return false
			}
		},

		/**
		 * 获取当前用户信息。
		 *
		 * 返回值约定（路由守卫依赖，勿随意改）：
		 *   - 成功     → currentUser 对象
		 *   - 未认证   → null
		 */
		async getUserInfoAction() {
			const ok = await this.ensureAuthenticated()
			return ok ? this.currentUser : null
		},

		// 兼容旧页面命名
		async fetchUserInfo() {
			return this.getUserInfoAction()
		},

		/**
		 * 退出登录：清本地状态 + 跳网关登出入口（网关会清会话）。
		 *
		 * <p>已知限制：授权服务器的会话仍在，再次访问网关会被静默重新登录。
		 * 真正的单点登出（SLO）需要 end_session_endpoint，属于 P3。</p>
		 */
		async logoutAction() {
			try {
				const body: any = await http.post('/api/login/outLogin')
				if (body?.data?.logoutUrl) {
					this.logoutUrl = body.data.logoutUrl
				}
			} catch {
				/* 忽略退出接口异常 */
			}
			this.markUnauthenticated()
			window.location.href = this.logoutUrl || DEFAULT_LOGOUT_URL
		},
		logout() {
			this.logoutAction()
		}
	}
})
