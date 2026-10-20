// @vitest-environment jsdom
// QA 回归 —— constantRoutes（/login /404 /redirect /iframe）与无 token 重定向
// 修正：/404、/redirect/*、/iframe/* 都不在 whiteList 里，必须携带 token 才能访问，
// 上一版断言写错了（QA 自身 bug，已修正）。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { state } = vi.hoisted(() => ({
	state: { menus: { code: 200, message: 'success', data: [
		{ id: 1, parentId: 0, menuType: 2, menuName: '首页', menuUrl: 'dashboard/index', menuIcon: 'home', menuStatus: 1, delFlag: 0 }
	], total: 0 } }
}))

vi.mock('@/utils/request', () => {
	const get = (url: string) => {
		if (url === '/api/currentUser') return Promise.resolve({ success: true, data: { name: 'admin', userid: '1', avatar: '' } })
		if (url.indexOf('/menu/list') > -1) return Promise.resolve(state.menus)
		return Promise.resolve({ code: 200, data: [] })
	}
	return {
		default: { get, post: () => Promise.resolve({ success: true, data: {} }) },
		service: { get, post: () => Promise.resolve({ success: true, data: {} }) }
	}
})

import { router } from '@/router'

const asLoggedOut = () => {
	localStorage.clear()
	setActivePinia(createPinia())
}
const asLoggedIn = () => {
	localStorage.clear()
	localStorage.setItem('token', 'GOOD')
	setActivePinia(createPinia())
}

describe('L. constantRoutes 回归', () => {
	beforeEach(() => asLoggedOut())

	it('L1 已登录：/404 可达', async () => {
		asLoggedIn()
		await router.push('/404')
		console.log('[L1] 落点 =', router.currentRoute.value.fullPath, 'matched =', router.currentRoute.value.matched.length)
		expect(router.currentRoute.value.path).toBe('/404')
		expect(router.currentRoute.value.matched.length).toBe(1)
	})

	it('L2 已登录：/redirect/:path 可达', async () => {
		asLoggedIn()
		await router.push('/redirect/system/user/index')
		console.log('[L2] 落点 =', router.currentRoute.value.fullPath, 'matched =', router.currentRoute.value.matched.length)
		expect(router.currentRoute.value.path).toBe('/redirect/system/user/index')
		expect(router.currentRoute.value.matched.length).toBe(2)
	})

	it('L3 已登录：/iframe/:query 可达', async () => {
		asLoggedIn()
		await router.push('/iframe/xxx')
		console.log('[L3] 落点 =', router.currentRoute.value.fullPath, 'matched =', router.currentRoute.value.matched.length)
		expect(router.currentRoute.value.path).toBe('/iframe/xxx')
		expect(router.currentRoute.value.matched.length).toBe(1)
	})

	it('L4 未登录访问深链 -> /login?redirect=原始目标（D3 已修复）', async () => {
		await router.push('/system/user/index').catch(() => {})
		console.log('[L4] 落点 =', router.currentRoute.value.fullPath)
		expect(router.currentRoute.value.path).toBe('/login')
		// D3 修复后：守卫用 to.redirectedFrom?.fullPath ?? to.fullPath 取回用户
		// 真正想去的地址，不再被 errorRoute 的 redirect:'/404' 改写成 /404。
		expect(router.currentRoute.value.query.redirect).toBe('/system/user/index')
	})

	it('L5 未登录访问 /login 正常停留', async () => {
		await router.push('/login')
		expect(router.currentRoute.value.path).toBe('/login')
		expect(router.currentRoute.value.matched.length).toBe(1)
	})

	it('L6 未登录访问 /404 会被拦到 /login（现状记录，非回归）', async () => {
		await router.push('/404').catch(() => {})
		console.log('[L6] 落点 =', router.currentRoute.value.fullPath)
		expect(router.currentRoute.value.path).toBe('/login')
	})

	it('L7 退出登录（清 token）后再访问 / 应跳 /login', async () => {
		asLoggedIn()
		await router.push('/')
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
		// 模拟 logoutAction：清 token + 重建 store
		localStorage.removeItem('token')
		asLoggedOut()
		// 注意：从 /dashboard/index 再 push('/') 会被判为 duplicated（vue-router 先应用
		// asyncRoutes.redirect），所以改用「从 /login 出发访问 /」这一真实场景。
		await router.push('/login')
		const failure = await router.push('/').catch((e) => e)
		console.log('[L7] push 返回 type =', JSON.stringify((failure && (failure as any).type) ?? failure))
		console.log('[L7] 退出后落点 =', router.currentRoute.value.fullPath)
		console.log('[L7] localStorage.token =', JSON.stringify(localStorage.getItem('token')))
		const { useUserStore } = await import('@/store/modules/user')
		console.log('[L7] userStore.token =', JSON.stringify(useUserStore().token))
		expect(router.currentRoute.value.path).toBe('/login')
	})

	it('L8 已登录态下 token 被清空后访问深链 -> /login', async () => {
		asLoggedIn()
		await router.push('/dashboard/index')
		expect(router.currentRoute.value.path).toBe('/dashboard/index')
		localStorage.removeItem('token')
		asLoggedOut()
		await router.push('/system/user/index').catch(() => {})
		console.log('[L8] 落点 =', router.currentRoute.value.fullPath)
		expect(router.currentRoute.value.path).toBe('/login')
	})
})
