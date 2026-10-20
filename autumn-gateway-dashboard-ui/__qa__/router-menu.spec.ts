// @vitest-environment jsdom
// QA 独立回归验证 —— 菜单/路由生成链路（不修改任何业务源码，仅验证）
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

vi.mock('@/api/basic', () => ({
	basicMenuApi: { listAll: vi.fn() },
	basicUserApi: {},
	basicRoleApi: {},
	basicAppApi: {},
	basicGroupApi: {},
	basicResourceApi: {},
	basicNamespaceApi: {},
	basicOperLogApi: {}
}))

import { basicMenuApi } from '@/api/basic'
import { useRouterStore } from '@/store/modules/router'
import { generateRoutes, normalizeMenuUrl, getKeepAliveRoutes, layoutModules } from '@/router'

const ok = (data: any) => ({ code: 200, message: 'success', data, total: 0 })

// 与后端 NamespaceDataInitializer 播种数据同构（menuName/menuUrl/menuIcon/parentId/menuType/menuStatus/delFlag）
const backendMenus = [
	{ id: 1, appId: 1, parentId: 0, menuType: 1, menuName: '系统管理', menuUrl: '', menuIcon: 'setting', menuStatus: 1, delFlag: 0 },
	{ id: 2, appId: 1, parentId: 0, menuType: 2, menuName: '首页', menuUrl: 'dashboard/index', menuIcon: 'home', menuStatus: 1, delFlag: 0 },
	{ id: 3, appId: 1, parentId: 1, menuType: 2, menuName: '用户管理', menuUrl: 'system/user/index', menuIcon: 'user', menuStatus: 1, delFlag: 0 },
	{ id: 4, appId: 1, parentId: 1, menuType: 2, menuName: '角色管理', menuUrl: 'system/role/index', menuIcon: 'key', menuStatus: 1, delFlag: 0 },
	// 脏数据：按钮 / 停用 / 已删
	{ id: 5, appId: 1, parentId: 1, menuType: 3, menuName: '新增按钮', menuUrl: 'system/user/index', menuIcon: '', menuStatus: 1, delFlag: 0 },
	{ id: 6, appId: 1, parentId: 1, menuType: 2, menuName: '停用菜单', menuUrl: 'system/menu/index', menuIcon: '', menuStatus: 2, delFlag: 0 },
	{ id: 7, appId: 1, parentId: 1, menuType: 2, menuName: '已删菜单', menuUrl: 'system/group/index', menuIcon: '', menuStatus: 1, delFlag: 1 },
	// 前导斜杠
	{ id: 8, appId: 1, parentId: 1, menuType: 2, menuName: '命名空间', menuUrl: '/system/namespace/index', menuIcon: '', menuStatus: 1, delFlag: 0 },
	// 页面文件不存在
	{ id: 9, appId: 1, parentId: 1, menuType: 2, menuName: '幽灵页', menuUrl: 'system/nonexistent/index', menuIcon: '', menuStatus: 1, delFlag: 0 },
	// parentId 指向被过滤掉的父节点
	{ id: 10, appId: 1, parentId: 7, menuType: 2, menuName: '孤儿菜单', menuUrl: 'system/app/index', menuIcon: '', menuStatus: 1, delFlag: 0 }
]

const paths = (routes: any[]): string[] => {
	const out: string[] = []
	const walk = (rs: any[]) => rs.forEach((r) => { out.push(r.path); if (r.children) walk(r.children) })
	walk(routes)
	return out
}

beforeEach(() => {
	setActivePinia(createPinia())
	vi.mocked(basicMenuApi.listAll).mockReset()
})

describe('A. normalizeMenuUrl', () => {
	it('去掉前导斜杠 / 尾斜杠 / query', () => {
		expect(normalizeMenuUrl('/system/user/index')).toBe('system/user/index')
		expect(normalizeMenuUrl('system/user/index')).toBe('system/user/index')
		expect(normalizeMenuUrl('/system/user/index/')).toBe('system/user/index')
		expect(normalizeMenuUrl('system/user/index?a=1&b=2')).toBe('system/user/index')
	})
	it('非法值返回空串', () => {
		expect(normalizeMenuUrl(null)).toBe('')
		expect(normalizeMenuUrl(undefined)).toBe('')
		expect(normalizeMenuUrl(123)).toBe('')
		expect(normalizeMenuUrl('')).toBe('')
	})
})

describe('B. 菜单契约：res.data 才是数组（疑点 3）', () => {
	it('{code,data:[...]} 能被正确解析成路由', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		expect(routes.length).toBeGreaterThan(0)
		const p = paths(routes)
		expect(p).toContain('/dashboard/index')
		expect(p).toContain('/system/user/index')
		expect(p).toContain('/system/role/index')
		console.log('[B] 解析出的路由:', JSON.stringify(p))
	})
})

describe('C. 脏数据过滤（S3）', () => {
	it('按钮(menuType=3)/停用(menuStatus=2)/已删(delFlag=1) 不产生路由', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		const p = paths(routes)
		// system/menu/index 只被停用菜单引用 -> 不应出现
		expect(p).not.toContain('/system/menu/index')
		// system/group/index 只被已删菜单引用 -> 不应出现
		expect(p).not.toContain('/system/group/index')
		// 标题层面确认
		const titles = JSON.stringify(routes)
		expect(titles).not.toContain('停用菜单')
		expect(titles).not.toContain('已删菜单')
		expect(titles).not.toContain('新增按钮')
	})
	it('parentId 指向被过滤掉的父节点时不会丢路由（S9）', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		expect(paths(routes)).toContain('/system/app/index')
	})
})

describe('D. 前导斜杠与缺失页面（S4 / S5）', () => {
	it('S4 前导斜杠 url 能命中真实页面文件', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		expect(paths(await store.getMenuRoutes())).toContain('/system/namespace/index')
	})
	it('S5 页面文件不存在时该菜单被丢弃且不抛异常', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		expect(paths(routes)).not.toContain('/system/nonexistent/index')
		// 每个路由必须有 component
		routes.forEach((r) => expect(r.component).toBeTruthy())
	})
})

describe('E. 兜底菜单（S1 接口 500 / S2 空数组）—— 关键', () => {
	it('S1 菜单接口 reject 时，兜底菜单必须解析出真实路由', async () => {
		vi.mocked(basicMenuApi.listAll).mockRejectedValue(new Error('500'))
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		console.log('[S1] 兜底解析结果:', JSON.stringify(paths(routes)))
		expect(routes.length).toBeGreaterThan(0)
		expect(paths(routes)).toContain('/dashboard/index')
	})
	it('S2 菜单接口返回空数组时，兜底菜单必须解析出真实路由', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok([]) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		console.log('[S2] 兜底解析结果:', JSON.stringify(paths(routes)))
		expect(routes.length).toBeGreaterThan(0)
		expect(paths(routes)).toContain('/dashboard/index')
	})
	it('S2b 菜单接口返回 {code:200,data:null} 时也要兜底', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(null) as any)
		const store = useRouterStore()
		const routes: any[] = await store.getMenuRoutes()
		expect(routes.length).toBeGreaterThan(0)
	})
})

describe('F. 兜底菜单引用的页面文件必须全部命中（疑点 4）', () => {
	it('fallbackMenu 里 9 个 url 都对应真实 .vue', () => {
		const mods = layoutModules()
		const urls = [
			'dashboard/index',
			'system/namespace/index',
			'system/app/index',
			'system/user/index',
			'system/role/index',
			'system/menu/index',
			'system/resource/index',
			'system/group/index',
			'system/operLog/index'
		]
		const missing = urls.filter((u) => !mods[`/src/views/${u}.vue`])
		console.log('[F] 缺失页面:', JSON.stringify(missing))
		expect(missing).toEqual([])
	})
	it('直接对 fallback 形态的嵌套菜单跑 generateRoutes 也能出路由', () => {
		const nested = [
			{ id: 1, name: '首页', url: 'dashboard/index', openStyle: 0, icon: 'home' },
			{
				id: 2, name: '系统管理', url: null, openStyle: 0, icon: 'setting',
				children: [{ id: 22, name: '用户管理', url: 'system/user/index', openStyle: 0, icon: 'user' }]
			}
		]
		const routes: any[] = generateRoutes(nested as any)
		console.log('[F2] 嵌套兜底菜单 -> 路由:', JSON.stringify(paths(routes)))
		expect(routes.length).toBeGreaterThan(0)
	})
})

describe('G. vue-router 真实注册后页面可达（防白屏）', () => {
	it('叶子路由以绝对路径挂在 "/" 下仍可被解析与导航', async () => {
		vi.mocked(basicMenuApi.listAll).mockResolvedValue(ok(backendMenus) as any)
		const store = useRouterStore()
		const menuRoutes: any[] = await store.getMenuRoutes()
		const keepAlive: any[] = getKeepAliveRoutes(menuRoutes, [])

		const rt = createRouter({
			history: createMemoryHistory(),
			routes: [{ path: '/login', component: { render: () => null } }]
		})
		const asyncRoutes: any = {
			path: '/',
			component: { render: () => null },
			redirect: '/dashboard/index',
			children: []
		}
		asyncRoutes.children.push(...keepAlive)
		rt.addRoute(asyncRoutes)
		rt.addRoute({ path: '/:pathMatch(.*)', redirect: '/404' })
		rt.addRoute({ path: '/404', component: { render: () => null } })

		const r1 = rt.resolve('/system/user/index')
		expect(r1.matched.length).toBeGreaterThan(1)
		expect(r1.matched[r1.matched.length - 1].path).toBe('/system/user/index')

		const r2 = rt.resolve('/system/namespace/index')
		expect(r2.matched.length).toBeGreaterThan(1)

		await rt.push('/')
		expect(rt.currentRoute.value.path).toBe('/dashboard/index')
	})
	it('内置 dashboardRoute 兜底：菜单全空时 /dashboard/index 仍可达', async () => {
		vi.mocked(basicMenuApi.listAll).mockRejectedValue(new Error('500'))
		const store = useRouterStore()
		const menuRoutes: any[] = await store.getMenuRoutes()
		const keepAlive: any[] = getKeepAliveRoutes(menuRoutes, [])
		const dashboardRoute: any = {
			path: '/dashboard/index',
			name: 'dashboardIndex',
			component: { render: () => null },
			meta: { title: '首页', icon: 'home', cache: true, affix: true, breadcrumb: [] }
		}
		if (!keepAlive.some((i) => i.path === dashboardRoute.path)) keepAlive.push(dashboardRoute)
		expect(keepAlive.map((i) => i.path)).toContain('/dashboard/index')

		const rt = createRouter({
			history: createMemoryHistory(),
			routes: [{ path: '/', component: { render: () => null }, redirect: '/dashboard/index', children: keepAlive }]
		})
		await rt.push('/')
		expect(rt.currentRoute.value.path).toBe('/dashboard/index')
		expect(rt.currentRoute.value.matched.length).toBe(2)
	})
})
