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

// 菜单不再硬编码：统一由后端 basic_menu 表维护（初始化器播种），前端启动时从
// /api/system/basic/menu/list 拉取并动态生成路由（见 store/modules/router）。
// generateRoutes 作为「扁平/树形菜单 → vue-router 路由」的通用转换器保留在下方。

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
