import { defineStore } from 'pinia'
import http from '@/utils/request'

export const useUserStore = defineStore('userStore', {
	state: () => ({
		// 用户信息（结构由后端 /api/currentUser 决定）
		user: {
			id: '',
			username: '',
			name: '',
			avatar: ''
		},
		currentUser: null as any,
		// 访问token
		token: localStorage.getItem('token') || ''
	}),
	getters: {
		isLogin: (state) => !!state.token,
		displayName: (state) =>
			state.currentUser?.name || state.user?.username || state.currentUser?.username || '未登录'
	},
	actions: {
		setToken(token: string) {
			this.token = token
			if (token) localStorage.setItem('token', token)
			else localStorage.removeItem('token')
		},
		// 账号登录：POST /api/login/account -> { status:'ok', token }
		async accountLoginAction(loginForm: any) {
			const body = await http.post('/api/login/account', { ...loginForm, type: 'account' })
			if (body && body.token) {
				this.setToken(body.token)
			}
			return body
		},
		// 获取当前用户信息
		async getUserInfoAction() {
			try {
				const body = await http.get('/api/currentUser')
				if (body && body.data) {
					this.currentUser = body.data
					this.user = { ...this.user, ...body.data, id: body.data.userid ?? body.data.id ?? this.user.id }
				}
			} catch {
				this.currentUser = null
			}
		},
		// 兼容旧页面命名
		async fetchUserInfo() {
			return this.getUserInfoAction()
		},
		// 退出登录
		async logoutAction() {
			try {
				await http.post('/api/login/outLogout')
			} catch {
				/* 忽略退出接口异常 */
			}
			this.setToken('')
			this.currentUser = null
		},
		logout() {
			this.logoutAction()
		}
	}
})
