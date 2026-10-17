import { defineStore } from 'pinia'
import { ITheme } from '@/store/theme/interface'
import cache from '@/utils/cache'

export const useAppStore = defineStore('appStore', {
	state: () => ({
		// sidebar 是否展开
		sidebarOpened: cache.getSidebarOpened(),
		// 国际化
		language: cache.getLanguage(),
		// 组件大小
		componentSize: cache.getComponentSize(),
		// 字典列表（本项目后端暂未提供字典接口，置空）
		dictList: [],
		// 主题
		theme: cache.getTheme()
	}),
	actions: {
		setSidebarOpened() {
			this.sidebarOpened = !this.sidebarOpened
			cache.setSidebarOpened(this.sidebarOpened)
		},
		setSidebarStatus(status: boolean) {
			this.sidebarOpened = status
			cache.setSidebarOpened(this.sidebarOpened)
		},
		setLanguage(locale: string) {
			this.language = locale
			cache.setLanguage(locale)
		},
		setComponentSize(size: string) {
			this.componentSize = size
			cache.setComponentSize(size)
		},
		// 字典接口由本项目后端提供时再实现；当前置空避免请求不存在的接口
		async getDictListAction() {
			this.dictList = []
		},
		setTheme(theme: ITheme) {
			this.theme = theme
			cache.setTheme(theme)
		}
	}
})
