import { defineStore } from 'pinia'
import { generateRoutes, autumnMenu } from '@/router'
import { RouteRecordRaw } from 'vue-router'

export const useRouterStore = defineStore('routerStore', {
	state: () => ({
		menuRoutes: [] as RouteRecordRaw[],
		searchMenu: [] as RouteRecordRaw[],
		routes: [] as RouteRecordRaw[]
	}),
	actions: {
		// 静态菜单，不再从后端拉取菜单树
		async getMenuRoutes() {
			const routes = generateRoutes(autumnMenu)
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
