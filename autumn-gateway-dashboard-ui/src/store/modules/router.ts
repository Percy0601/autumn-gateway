import { defineStore } from 'pinia'
import { generateRoutes } from '@/router'
import { basicMenuApi } from '@/api/basic'
import { RouteRecordRaw } from 'vue-router'

// 静态兜底菜单：仅当后端 /api/system/basic/menu/list 不可用时使用，保证页面仍可加载。
// 正常情况菜单统一由 basic_menu 表维护（后端初始化器播种），前端不再硬编码。
const fallbackMenu = [
	{
		id: 1,
		name: '首页',
		url: 'dashboard/index',
		openStyle: 0,
		icon: 'home',
		affix: true
	},
	{
		id: 2,
		name: '系统管理',
		url: null,
		openStyle: 0,
		icon: 'setting',
		children: [
			{ id: 25, name: '命名空间', url: 'system/namespace/index', openStyle: 0, icon: 'apartment' },
			{ id: 21, name: '应用管理', url: 'system/app/index', openStyle: 0, icon: 'appstore' },
			{ id: 22, name: '用户管理', url: 'system/user/index', openStyle: 0, icon: 'user' },
			{ id: 23, name: '角色管理', url: 'system/role/index', openStyle: 0, icon: 'key' },
			{ id: 24, name: '菜单管理', url: 'system/menu/index', openStyle: 0, icon: 'menu' },
			{ id: 26, name: '资源管理', url: 'system/resource/index', openStyle: 0, icon: 'file' },
			{ id: 27, name: '用户组管理', url: 'system/group/index', openStyle: 0, icon: 'group' },
			{ id: 34, name: '日志管理', url: 'system/operLog/index', openStyle: 0, icon: 'file-text' }
		]
	}
]

// 将后端 basic_menu 扁平列表转换为 generateRoutes 需要的结构（含父子树）
const buildMenuTree = (menus: any[]): any[] => {
	const map = new Map<number, any>()
	menus.forEach((m) => {
		map.set(m.id, {
			id: m.id,
			name: m.menuName,
			url: m.menuUrl || null,
			icon: m.menuIcon,
			openStyle: 0,
			children: []
		})
	})
	const roots: any[] = []
	menus.forEach((m) => {
		const node = map.get(m.id)
		if (!node) return
		if (m.parentId && m.parentId !== 0 && map.has(m.parentId)) {
			map.get(m.parentId)!.children.push(node)
		} else {
			roots.push(node)
		}
	})
	const clean = (list: any[]) =>
		list.map((n) => {
			if (n.children && n.children.length) {
				n.children = clean(n.children)
			} else {
				delete n.children
			}
			return n
		})
	return clean(roots)
}

export const useRouterStore = defineStore('routerStore', {
	state: () => ({
		menuRoutes: [] as RouteRecordRaw[],
		searchMenu: [] as RouteRecordRaw[],
		routes: [] as RouteRecordRaw[]
	}),
	actions: {
		// 菜单统一从 basic_menu 表读取（由后端初始化器播种），不再硬编码
		async getMenuRoutes() {
			let menus: any[] = []
			try {
				const res: any = await basicMenuApi.listAll()
				menus = res?.data || []
			} catch (e) {
				console.error('加载菜单失败，使用静态兜底菜单', e)
				menus = fallbackMenu
			}
			const tree = buildMenuTree(menus)
			const routes = generateRoutes(tree)
			this.menuRoutes.push(...routes)
			return this.menuRoutes
		},
		setSearchMenu(routers: RouteRecordRaw[]) {
			this.searchMenu = routers
		},
		setRoutes(routers: RouteRecordRaw[]) {
			this.routes = routers
		}
	}
})
