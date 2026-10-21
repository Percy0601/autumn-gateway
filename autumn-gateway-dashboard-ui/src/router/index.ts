import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/store/modules/user'
import { useRouterStore } from '@/store/modules/router'
import { isExternalLink, pathToCamel } from '@/utils/tool'

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

// 内置首页路由：无论后端菜单返回什么，都保证 /dashboard/index 一定存在。
// 否则 '/' 的 redirect 指向不存在的路由时，vue-router 解析失败且不渲染任何组件 —— 直接白屏。
const dashboardRoute: RouteRecordRaw = {
	path: '/dashboard/index',
	name: 'dashboardIndex',
	component: () => import('../views/dashboard/index.vue'),
	meta: {
		title: '首页',
		icon: 'home',
		cache: true,
		affix: true,
		breadcrumb: []
	}
}

// 动态路由只允许注册一次。重复注册不仅产生重复路由，还会让守卫反复走
// 「拉菜单 → addRoute → next({...to, replace:true})」分支，最终被 vue-router
// 判定为无限重定向而中断导航，页面停在空白状态。
let routesRegistered = false

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

/**
 * 统一认证模式下的路由守卫。
 *
 * <p>与旧实现最大的不同：**前端不再持有令牌**。令牌只存在于网关的服务端会话，
 * 浏览器侧是一枚 HttpOnly Cookie。所以「有没有登录」不能再看 localStorage，
 * 只能调用 {@code /api/currentUser} 问后端 —— 网关会在转发前把 access_token 注入请求头。</p>
 */
router.beforeEach(async (to, from, next) => {
	NProgress.start()

	const userStore = useUserStore()
	const routerStore = useRouterStore()

	// 登录页在统一认证模式下只是一个「点击去登录」的落地页，不参与鉴权判定
	if (to.path === '/login') {
		next()
		return
	}

	// 会话已确认过就直接放行，避免每次导航都打一次接口
	const authed = await userStore.ensureAuthenticated()
	if (!authed) {
		// 未认证：把浏览器送到网关的 OAuth2 登录入口。
		// 授权服务器侧若还留着会话，这一步会无感知地自动完成，用户压根看不到登录页。
		//
		// 注意这里不能调 next()：整页跳转会中断本次 SPA 导航，
		// 再 next 一次反而会产生一次已被取消的路由跳转。
		userStore.redirectToLogin()
		return
	}

	// 兜底：后端没返回用户标识时也要有非空值，避免守卫反复重入
	if (!userStore.user.id) {
		userStore.user.id = userStore.currentUser?.userid || userStore.currentUser?.id || 'unknown'
	}

	// 动态路由只注册一次。重复注册会产生重复路由，并让守卫反复走
	// 「拉菜单 → addRoute → next({...to, replace:true})」分支，
	// 最终被 vue-router 判定为无限重定向而中断导航，页面停在空白状态。
	if (!routesRegistered) {
		const menuRoutes = await routerStore.getMenuRoutes()
		const keepAliveRoutes = getKeepAliveRoutes(menuRoutes, [])

		// 保证首页一定可达，否则 '/' 的 redirect 会落空导致白屏
		const hasDashboard = keepAliveRoutes.some((item: any) => item.path === dashboardRoute.path)
		if (!hasDashboard) {
			keepAliveRoutes.push(dashboardRoute)
		}

		asyncRoutes.children?.push(...keepAliveRoutes)
		router.addRoute(asyncRoutes)
		router.addRoute(errorRoute)
		routerStore.setRoutes(constantRoutes.concat(asyncRoutes))
		routerStore.setSearchMenu(keepAliveRoutes)
		routesRegistered = true

		// 路由刚注册完，重放一次当前导航让新路由生效
		next({ ...to, replace: true })
		return
	}

	next()
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
			if (Array.isArray(item.meta?.breadcrumb)) {
				item.meta.breadcrumb.push(...breadcrumb)
			}
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

// 归一化菜单 url：去掉前后 '/'、去掉 query 串，保证能命中 import.meta.glob 的 key
export const normalizeMenuUrl = (url: any): string => {
	if (!url || typeof url !== 'string') return ''
	let menuUrl = url.trim()
	if (menuUrl.indexOf('?') > -1) {
		menuUrl = menuUrl.split('?')[0]
	}
	// 去掉开头的 '/'（后端可能存成 '/system/user/index'），也去掉结尾的 '/'
	menuUrl = menuUrl.replace(/^\/+/, '').replace(/\/+$/, '')
	return menuUrl
}

export const generateRoutes = (menuList: any): RouteRecordRaw[] => {
	const routerList: RouteRecordRaw[] = []

	menuList.forEach((menu: any) => {
		let component
		let path
		const menuUrl = normalizeMenuUrl(menu.url)

		if (menu.children && menu.children.length > 0) {
			component = () => import('@/layout/index.vue')
			path = '/p/' + menu.id
		} else {
			if (isIframeUrl(menu)) {
				component = () => import('@/layout/components/Router/Iframe.vue')
				path = '/iframe/' + menu.id
			} else {
				// 组件解析不到（页面文件不存在 / url 为空）时直接丢弃该菜单，
				// 绝不能注册 component 为 undefined 的路由 —— 那样会渲染出空白页。
				component = getDynamicComponent(menuUrl)
				if (!component) {
					console.warn('[router] 菜单无对应页面，已忽略：', menu.name, menu.url)
					return
				}
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
