import { defineStore } from 'pinia'
import http from '@/utils/request'

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
		// 访问token
		token: localStorage.getItem('token') || '',
		// 按钮级权限标识清单（后端 /api/currentUser 的 authorityList），供 v-auth 指令使用。
		// 超管的清单里带一个 "*"，v-auth 见 "*" 即视为全放行。
		authorityList: [] as string[],
		// 是否超级管理员（后端 /api/currentUser 的 superAdmin）
		superAdmin: false
	}),
	getters: {
		isLogin: (state) => !!state.token,
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
		setToken(token: string) {
			this.token = token
			if (token) localStorage.setItem('token', token)
			else localStorage.removeItem('token')
		},
		// 账号登录：POST /api/login/account -> { status:'ok', token }
		// loginForm 需带 username / password / captcha（验证码由登录页 <img src="/api/captcha"> 获取，
		// captchaId 由后端 HttpOnly Cookie 下发并自动携带，无需前端传）
		async accountLoginAction(loginForm: any) {
			// 响应拦截器已把 AxiosResponse 解包成业务 body，这里显式按 any 收窄
			const body: any = await http.post('/api/login/account', { ...loginForm, type: 'account' })
			if (body && body.token) {
				this.setToken(body.token)
			}
			return body
		},
		// 获取当前用户信息。
		//
		// 返回值约定（路由守卫依赖，勿随意改）：
		//   - 成功            → 返回 currentUser 对象
		//   - 200 但无 data   → 返回 null      （服务端明确表示认不出身份 → 守卫应回登录页）
		//   - 网络抖动等错误  → 返回 undefined （守卫保守放行，避免误踢）
		//   - 401 / 403       → 抛异常         （守卫清 token 并跳登录页）
		async getUserInfoAction() {
			let body: any = null
			try {
				body = await http.get('/api/currentUser')
			} catch (error: any) {
				this.currentUser = null
				// 鉴权失败必须向上抛，交给路由守卫清 token 并跳登录页；
				// 其他错误（网络抖动、超时等）保持静默，避免误踢用户
				if (error?.response?.status === 401 || error?.response?.status === 403) {
					throw error
				}
				return undefined
			}

			// 请求成功但服务端没给出用户数据 → 等同未认证，返回 null 让守卫判空。
			// 注意：这段必须放在 try 之外，否则会被上面的 catch 当作普通错误静默掉，
			// 守卫就会把 user.id 兜底成 'unknown' 直接放进首页。
			if (!body || !body.data) {
				this.currentUser = null
				this.authorityList = []
				this.superAdmin = false
				return null
			}

			this.currentUser = body.data
			this.user = { ...this.user, ...body.data, id: body.data.userid ?? body.data.id ?? this.user.id }
			// 权限清单提到 store 顶层：v-auth 指令读的是 store.authorityList，
			// 而后端下发在 data.authorityList 里，不搬上来按钮权限永远不会生效
			this.authorityList = Array.isArray(body.data.authorityList) ? body.data.authorityList : []
			this.superAdmin = !!body.data.superAdmin
			return this.currentUser
		},
		// 兼容旧页面命名
		async fetchUserInfo() {
			return this.getUserInfoAction()
		},
		// 退出登录
		async logoutAction() {
			try {
				await http.post('/api/login/outLogin')
			} catch {
				/* 忽略退出接口异常 */
			}
			this.setToken('')
			this.currentUser = null
			this.authorityList = []
			this.superAdmin = false
		},
		logout() {
			this.logoutAction()
		}
	}
})
