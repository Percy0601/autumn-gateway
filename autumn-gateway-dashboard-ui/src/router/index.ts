import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import { useRouterStore } from '@/store/modules/router'
import { isExternalLink, pathToCamel } from '@/utils/tool'
import constant from '@/utils/constant'

NProgress.configure({ showSpinner: false })

const constantRoutes: RouteRecordRaw[] = [
	{
		path: '/redirect',
		component: () => import('../layout/index.vue'),
		children: [
			{
				path: '/redirect/:path(.*)',
				component: () => import('../layout/components/Router/Redirect.vue')
			}
		]
	},
	{
		path: '/iframe/:query?',
		component: () => import('../layout/components/Router/Iframe.vue')
	},
	{
		path: '/login',
		component: () => import('../views/login/index.vue')
	},
	{
		path: '/404',
		component: () => import('../views/404.vue')
	}
]

const asyncRoutes: RouteRecordRaw = {
	path: '/',
	component: () => import('../layout/index.vue'),
	redirect: '/dashboard/index',
	children: []
}

// 本项目静态菜单（对接 autumn-gateway-dashboard 后端，不依赖后端动态菜单下发）
export const autumnMenu = [
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
			{ id: 28, name: '用户组-角色', url: 'system/group-role/index', openStyle: 0, icon: 'link' },
			{ id: 29, name: '用户-用户组', url: 'system/user-group/index', openStyle: 0, icon: 'link' },
			{ id: 30, name: '用户-角色', url: 'system/user-role/index', openStyle: 0, icon: 'link' },
			{ id: 31, name: '用户组-用户组', url: 'system/group-group/index', openStyle: 0, icon: 'link' },
			{ id: 32, name: '角色-菜单', url: 'system/role-menu/index', openStyle: 0, icon: 'menu' },
			{ id: 33, name: '角色-资源', url: 'system/role-resource/index', openStyle: 0, icon: 'file' },
			{ id: 34, name: '日志管理', url: 'system/oper-log/index', openStyle: 0, icon: 'file-text' }
		]
	}
]

export const errorRoute: RouteRecordRaw = {
	path: '/:pathMatch(.*)',
	redirect: '/404'
}

export const router = createRouter({
	history: createWebHistory(),
	routes: constantRoutes
})

const whiteList = ['/login']

router.beforeEach(async (to, from, next) => {
	NProgress.start()

	const appStore = useAppStore()
	const userStore = useUserStore()
	const routerStore = useRouterStore()

	if (userStore.token) {
		if (to.path === '/login') {
			next(constant.loginPage)
		} else {
			if (!userStore.user.id) {
				try {
					await userStore.getUserInfoAction()
				} catch (error) {
					userStore.setToken('')
					next('/login')
					return Promise.reject(error)
				}

				// 静态菜单 + 首页
				const menuRoutes = await routerStore.getMenuRoutes()
				const keepAliveRoutes = getKeepAliveRoutes(menuRoutes, [])
				asyncRoutes.children?.push(...keepAliveRoutes)
				router.addRoute(asyncRoutes)
				router.addRoute(errorRoute)
				routerStore.setRoutes(constantRoutes.concat(asyncRoutes))
				routerStore.setSearchMenu(keepAliveRoutes)
				next({ ...to, replace: true })
			} else {
				next()
			}
		}
	} else {
		if (whiteList.indexOf(to.path) > -1) {
			next()
		} else {
			if (to.fullPath === '/') {
				next('/login')
			} else {
				next(`/login?redirect=${to.fullPath}`)
			}
		}
	}
})

router.afterEach(() => {
	NProgress.done()
})

export const getKeepAliveRoutes = (rs: RouteRecordRaw[], breadcrumb: string[]): RouteRecordRaw[] => {
	const routerList: RouteRecordRaw[] = []

	rs.forEach((item: any) => {
		if (item.meta.title) {
			breadcrumb.push(item.meta.title)
		}

		if (item.children && item.children.length > 0) {
			routerList.push(...getKeepAliveRoutes(item.children, breadcrumb))
		} else {
			item.meta.breadcrumb.push(...breadcrumb)
			routerList.push(item)
		}

		breadcrumb.pop()
	})
	return routerList
}

export interface ModuleMap {
	[key: string]: any
}

export const layoutModules = () => {
	return import.meta.glob('/src/views/**/*.vue')
}

const getDynamicComponent = (path: string): any => {
	const modules: ModuleMap = layoutModules()
	return modules[`/src/views/${path}.vue`]
}

export const generateRoutes = (menuList: any): RouteRecordRaw[] => {
	const routerList: RouteRecordRaw[] = []

	menuList.forEach((menu: any) => {
		let component
		let path
		let menuUrl = menu.url
		if (menuUrl && menuUrl.indexOf('?') > -1) {
			menuUrl = menuUrl.split('?')[0]
		}

		if (menu.children && menu.children.length > 0) {
			component = () => import('@/layout/index.vue')
			path = '/p/' + menu.id
		} else {
			if (isIframeUrl(menu)) {
				component = () => import('@/layout/components/Router/Iframe.vue')
				path = '/iframe/' + menu.id
			} else {
				component = getDynamicComponent(menuUrl)
				path = '/' + menuUrl
			}
		}
		const route: RouteRecordRaw = {
			path: path,
			name: pathToCamel(path),
			component: component,
			children: [],
			meta: {
				title: menu.name,
				icon: menu.icon,
				id: '' + menu.id,
				url: menu.url,
				cache: true,
				newOpen: menu.openStyle === 1,
				affix: menu.affix,
				breadcrumb: []
			}
		}

		if (menu.children && menu.children.length > 0) {
			route.children?.push(...generateRoutes(menu.children))
		}

		routerList.push(route)
	})
	return routerList
}

const isIframeUrl = (menu: any): boolean => {
	if (menu.openStyle === 1) {
		return false
	}
	return isExternalLink(menu.url)
}
