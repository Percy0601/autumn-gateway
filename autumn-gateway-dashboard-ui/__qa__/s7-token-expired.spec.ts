// @vitest-environment jsdom
// QA 独立回归验证 —— S7 token 失效（独立文件，保证 routesRegistered 从 false 开始）
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { state } = vi.hoisted(() => ({ state: { menus: null as any } }))

vi.mock('@/utils/request', () => {
	const get = (url: string) => {
		if (url === '/api/currentUser') {
			return Promise.reject(Object.assign(new Error('Request failed with status code 401'), { response: { status: 401 } }))
		}
		if (url.indexOf('/menu/list') > -1) {
			return Promise.reject(Object.assign(new Error('Request failed with status code 401'), { response: { status: 401 } }))
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
import { useRouterStore } from '@/store/modules/router'

describe('S7 token 失效（currentUser 401 + 菜单 401），从零初始化', () => {
	beforeEach(() => {
		localStorage.clear()
		localStorage.setItem('token', 'EXPIRED')
		setActivePinia(createPinia())
		state.menus = null
	})

	it('S7: 守卫落点 / user.id / in-memory token / 路由表 / 侧边栏菜单', async () => {
		const before = router.getRoutes().length
		await router.push('/')
		const after = router.getRoutes().length

		const userStore = useUserStore()
		const routerStore = useRouterStore()

		console.log('--- S7 结果 ---')
		console.log('落点            :', router.currentRoute.value.fullPath)
		console.log('当前路由 name   :', String(router.currentRoute.value.name))
		console.log('matched 深度    :', router.currentRoute.value.matched.length)
		console.log('user.id         :', JSON.stringify(userStore.user.id))
		console.log('userStore.token :', JSON.stringify(userStore.token))
		console.log('localStorage    :', JSON.stringify(localStorage.getItem('token')))
		console.log('menuRoutes(侧栏):', JSON.stringify(routerStore.menuRoutes.map((r: any) => r.path)))
		console.log('searchMenu      :', JSON.stringify(routerStore.searchMenu.map((r: any) => r.path)))
		console.log('路由数量 before/after:', before, '->', after)
		console.log('已注册路由      :', JSON.stringify(router.getRoutes().map((r) => r.path)))

		// D2 修复后：token 失效必须落到 /login，且本地 token 被清掉
		expect(router.currentRoute.value.matched.length).toBeGreaterThan(0)
		expect(router.currentRoute.value.path).toBe('/login')
		expect(userStore.token).toBe('')
		expect(localStorage.getItem('token')).toBeNull()
	})

	it('S7b: 落到 /login 后应能停留（不再被弹回首页）', async () => {
		await router.push('/')
		console.log('--- S7b ---')
		console.log('首次落点:', router.currentRoute.value.fullPath)
		await router.push('/login').catch((e) => console.log('push /login 异常:', String(e)))
		console.log('再访问 /login 后落点:', router.currentRoute.value.fullPath)
		console.log('userStore.token:', JSON.stringify(useUserStore().token))
		expect(router.currentRoute.value.path).toBe('/login')
		expect(useUserStore().token).toBe('')
	})

	it('S7c: 200 但无 data（非 401）时也要回 /login', async () => {
		// 复用同文件的 mock：/api/currentUser 固定 401；这里改为断言
		// 「守卫对 null 返回值」的处理 —— 用 200 空包覆盖需要换 mock，
		// 因此单独在 guard-token.spec.ts 的 H1c 里验证返回值契约。
		await router.push('/')
		expect(router.currentRoute.value.path).toBe('/login')
	})
})
