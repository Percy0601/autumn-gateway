// @vitest-environment jsdom
// QA 独立回归验证 —— 真实 router 守卫端到端（模拟 S7 token 失效 / 正常登录 / 无 token）
// 注意：本文件把 '@/utils/request' 整体替换，因此不覆盖 axios 拦截器里的
// localStorage.removeItem + location.hash='#/login' 副作用，只验证「守卫自身」的走向。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { state } = vi.hoisted(() => ({
	state: { currentUser: null as any, menus: null as any }
}))

vi.mock('@/utils/request', () => {
	const get = (url: string) => {
		if (url === '/api/currentUser') {
			// 'EMPTY' = 接口 200 但没有 data（非异常路径），守卫必须按未认证处理
			if (state.currentUser === 'EMPTY') return Promise.resolve({ success: true })
			return state.currentUser === null
				? Promise.reject(Object.assign(new Error('401'), { response: { status: 401 } }))
				: Promise.resolve({ success: true, data: state.currentUser })
		}
		if (url.indexOf('/menu/list') > -1) {
			return state.menus === null
				? Promise.reject(Object.assign(new Error('401'), { response: { status: 401 } }))
				: Promise.resolve(state.menus)
		}
		return Promise.resolve({ code: 200, data: [] })
	}
	return {
		default: { get, post: () => Promise.resolve({ success: true, data: {} }) },
		service: { get, post: () => Promise.resolve({ success: true, data: {} }) }
	}
})

import { router } from '@/router'
import { useUserStore } from '@/store/modules/user'

const flatBackendMenus = [
	{ id: 1, parentId: 0, menuType: 1, menuName: '系统管理', menuUrl: '', menuIcon: 'setting', menuStatus: 1, delFlag: 0 },
	{ id: 2, parentId: 0, menuType: 2, menuName: '首页', menuUrl: 'dashboard/index', menuIcon: 'home', menuStatus: 1, delFlag: 0 },
	{ id: 3, parentId: 1, menuType: 2, menuName: '用户管理', menuUrl: 'system/user/index', menuIcon: 'user', menuStatus: 1, delFlag: 0 }
]

const reset = async () => {
	localStorage.clear()
	state.currentUser = null
	state.menus = null
	// 每个用例前回到 /login，并清掉模块级 stores 的影响
	router.replace('/login')
	await router.isReady()
}

describe('K. 守卫端到端', () => {
	beforeEach(async () => {
		setActivePinia(createPinia())
		await reset()
	})

	it('K1 无 token 访问 / 应跳转 /login', async () => {
		await router.push('/')
		expect(router.currentRoute.value.path).toBe('/login')
	})

	it('K2 直接访问 /login 正常停留', async () => {
		await router.push('/login')
		expect(router.currentRoute.value.path).toBe('/login')
	})

	it('K3 有 token + 菜单正常：应到达 /dashboard/index 且菜单非空', async () => {
		localStorage.setItem('token', 'GOOD')
		// 强制重建 store 读取新 token
		setActivePinia(createPinia())
		state.currentUser = { name: 'admin', avatar: '', userid: '1', access: 'admin' }
		state.menus = { code: 200, message: 'success', data: flatBackendMenus, total: 0 }

		await router.push('/')
		console.log('[K3] 落点 =', router.currentRoute.value.fullPath)
		const { useRouterStore } = await import('@/store/modules/router')
		const store = useRouterStore()
		console.log('[K3] menuRoutes =', JSON.stringify(store.menuRoutes.map((r: any) => r.path)))
		console.log('[K3] searchMenu =', JSON.stringify(store.searchMenu.map((r: any) => r.path)))
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
		expect(store.menuRoutes.length).toBeGreaterThan(0)
		expect(store.searchMenu.length).toBeGreaterThan(0)
	})

	it('K4 [S7] token 失效（currentUser 401 + 菜单 401）：守卫究竟去哪？', async () => {
		localStorage.setItem('token', 'EXPIRED')
		setActivePinia(createPinia())
		state.currentUser = null
		state.menus = null

		await router.push('/')
		const userStore = useUserStore()
		console.log('[K4] 落点 =', router.currentRoute.value.fullPath)
		console.log('[K4] user.id =', JSON.stringify(userStore.user.id))
		console.log('[K4] userStore.token =', JSON.stringify(userStore.token))
		console.log('[K4] localStorage.token =', JSON.stringify(localStorage.getItem('token')))
		const { useRouterStore } = await import('@/store/modules/router')
		const store = useRouterStore()
		console.log('[K4] menuRoutes =', JSON.stringify(store.menuRoutes.map((r: any) => r.path)))
		// 断言：守卫自身的走向（不是 /login 就是 fallback 首页）
		expect(['/dashboard/index', '/login']).toContain(router.currentRoute.value.path)
	})

	it('K7 [S7 变体] /api/currentUser 返回 200 但无 data -> 也应回 /login', async () => {
		localStorage.setItem('token', 'ODD')
		setActivePinia(createPinia())
		state.currentUser = 'EMPTY' as any
		state.menus = { code: 200, message: 'success', data: flatBackendMenus, total: 0 }

		// 用深链而不是 '/'：'/' 会被 asyncRoutes.redirect 改写成 /dashboard/index，
		// 而当前路由恰好是 /dashboard/index，vue-router 会判为 duplicated 直接中止
		// 导航（NavigationFailureType=16），守卫根本不会跑 —— 这是测试写法问题，
		// 不是实现问题。
		await router.push('/system/user/index').catch(() => {})
		const userStore = useUserStore()
		console.log('[K7] 落点 =', router.currentRoute.value.fullPath)
		console.log('[K7] userStore.token =', JSON.stringify(userStore.token))
		console.log('[K7] localStorage.token =', JSON.stringify(localStorage.getItem('token')))
		expect(router.currentRoute.value.path).toBe('/login')
		expect(userStore.token).toBe('')
	})

	it('K8 [R4 已修] 会话中 401：守卫按 localStorage 对齐，清空 Pinia token 并送回登录页', async () => {
		localStorage.setItem('token', 'GOOD')
		setActivePinia(createPinia())
		state.currentUser = { name: 'admin', userid: '1' }
		state.menus = { code: 200, message: 'success', data: flatBackendMenus, total: 0 }
		await router.push('/')
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
		const routesBefore = router.getRoutes().length

		// 模拟 utils/request.ts:48 的行为：401 时只 localStorage.removeItem('token')
		localStorage.removeItem('token')

		// 用户继续点菜单导航
		await router.push('/system/user/index').catch(() => {})

		const userStore = useUserStore()
		console.log('[K8] 落点 =', router.currentRoute.value.fullPath)
		console.log('[K8] userStore.token =', JSON.stringify(userStore.token))
		console.log('[K8] localStorage.token =', JSON.stringify(localStorage.getItem('token')))

		// R4 修复后：守卫每次导航前用 localStorage 对齐 Pinia，
		// Pinia token 被同步清空，用户被送回登录页（不再卡在业务页等手动 F5）。
		expect(userStore.token).toBe('')
		expect(router.currentRoute.value.path).toBe('/login')
		// R4 与 D3 的联合契约：踢回登录页时必须带回原始目标，登录后才能回到原页面
		expect(router.currentRoute.value.query.redirect).toBe('/system/user/index')
		// 对齐 token 不能产生副作用（不得触发重复注册路由）
		expect(router.getRoutes().length).toBe(routesBefore)
	})

	it('K5 已登录后再访问 /login 应被弹回 /dashboard/index（constant.loginPage）', async () => {
		localStorage.setItem('token', 'GOOD')
		setActivePinia(createPinia())
		state.currentUser = { name: 'admin', userid: '1' }
		state.menus = { code: 200, message: 'success', data: flatBackendMenus, total: 0 }
		await router.push('/')
		await router.push('/login')
		console.log('[K5] 落点 =', router.currentRoute.value.fullPath)
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
	})

	it('K6 刷新/二次导航不应重复注册路由（routesRegistered 生效）', async () => {
		localStorage.setItem('token', 'GOOD')
		setActivePinia(createPinia())
		state.currentUser = { name: 'admin', userid: '1' }
		state.menus = { code: 200, message: 'success', data: flatBackendMenus, total: 0 }
		await router.push('/')
		expect(router.currentRoute.value.path).toBe('/dashboard/index')

		// 用 router.getRoutes() 量路由表规模 —— 这是模块级真实状态，
		// 不会被「新建 pinia 导致 searchMenu 归零」干扰。
		const count1 = router.getRoutes().length
		await router.push('/system/user/index')
		await router.push('/dashboard/index')
		const count2 = router.getRoutes().length
		console.log('[K6] 路由数量 第一次 =', count1, ' 第二次 =', count2)
		expect(count2).toBe(count1)
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
	})
})
