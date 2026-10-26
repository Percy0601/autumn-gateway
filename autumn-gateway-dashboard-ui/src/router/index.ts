import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
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

const whiteList = ['/login']

/**
 * Bearer JWT 模式下的路由守卫。
 *
 * <p>判定依据就是本地有没有 token：令牌由 dashboard 服务的 {@code /api/login/account}
 * 签发后存在 localStorage，每个请求由 utils/request.ts 以
 * {@code Authorization: Bearer <jwt>} 带上；有 token 再调 {@code /api/currentUser}
 * 换取用户信息与权限。</p>
 */
router.beforeEach(async (to, from, next) => {
	NProgress.start()

	const userStore = useUserStore()
	const routerStore = useRouterStore()

	// localStorage 是 token 的唯一真实来源：utils/request.ts 在 401 时只清 localStorage，
	// 而 Pinia 的 token 是 store 创建时读一次、之后不再同步。会话中途 token 过期时
	// 两边会不一致（Pinia 还留着旧值），守卫就会误判「仍已登录」，把用户卡在业务页，
	// 每点一次按钮弹一次 401 toast，只能手动 F5。所以每次导航前先对齐。
	const storedToken = localStorage.getItem('token') || ''
	if (userStore.token !== storedToken) {
		userStore.setToken(storedToken)
	}

	if (userStore.token) {
		if (to.path === '/login') {
			next(constant.loginPage)
			return
		}

		// 1) 用户信息：user.id 为空说明当前会话还没初始化过用户信息。
		// 无论成功失败都必须有确定出口：成功就继续注册路由；失败（抛异常或返回 null）
		// 就清 token 回登录页。绝不能让流程落回「什么都不做」，
		// 否则每次导航都重入该分支 → 无限重定向 → 导航中断 → 白屏。
		if (!userStore.user.id) {
			let currentUser: any
			try {
				currentUser = await userStore.getUserInfoAction()
			} catch (error) {
				// 鉴权失败（401/403）：**先清 token** 再回登录页。
				// 顺序很关键：不清 token 的话 userStore.token 仍有值，
				// 上面 to.path === '/login' 分支会把人 next(constant.loginPage) 弹回首页。
				userStore.setToken('')
				next('/login')
				// 用 return 而非 Promise.reject：守卫声明了 (to, from, next) 三形参，
				// vue-router 仍会监听返回的 promise，一旦 reject 就会中断导航，
				// 把 next('/login') 的跳转也一并取消掉。
				return
			}

			// null = 接口 200 但没有用户数据，服务端明确认不出身份 → 回登录页。
			// undefined = 网络抖动等非鉴权错误 → 保守放行，避免误踢用户。
			if (currentUser === null) {
				userStore.setToken('')
				next('/login')
				return
			}

			// 兜底：后端没返回用户标识时也要有个非空值，避免守卫反复重入
			if (!userStore.user.id) {
				userStore.user.id = userStore.currentUser?.userid || userStore.currentUser?.id || 'unknown'
			}
		}

		// 2) 动态路由注册：必须与「是否首次拉用户信息」解耦。
		//
		// 登录页在提交成功后已经调过 fetchUserInfo，user.id 此时非空；
		// 若把注册逻辑嵌在「user.id 为空」分支里就会被跳过 —— 路由表里没有
		// /dashboard/index，登录页的 router.replace 无处可去，表现为
		// 「登录成功但页面空白，必须手动 F5 才正常（刷新后 store 重建、
		// user.id 为空，注册逻辑才被走到）」。这就是「登录后需要手动刷新」的根因。
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
	} else {
		if (whiteList.indexOf(to.path) > -1) {
			next()
		} else {
			// 未登录访问深链时，目标可能先被 errorRoute 的 redirect:'/404' 改写，
			// 此时 to.fullPath 已经是 '/404'（登录后会跳 404 页）。
			// 用 redirectedFrom 拿回用户真正想去的地址。
			const target = to.redirectedFrom?.fullPath ?? to.fullPath
			if (target === '/') {
				next('/login')
			} else {
				next(`/login?redirect=${encodeURIComponent(target)}`)
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
