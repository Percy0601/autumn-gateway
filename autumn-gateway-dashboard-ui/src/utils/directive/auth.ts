import type { App } from 'vue'
import { useUserStore } from '@/store/modules/user'

export function auth(app: App) {
	// 权限验证
	app.directive('auth', {
		mounted(el, binding) {
			const userStore = useUserStore()

			// userStore 上并没有 authorityList 字段（后端也未下发权限点清单）。
			// 原实现直接 userStore.authorityList.some(...) 会在权限列表缺失时抛
			// TypeError，进而中断整个组件树的挂载 —— 必须做空值兜底。
			const authorityList: string[] = (userStore as any).authorityList || []

			// 拿不到权限清单时不隐藏任何元素，保持「可用」优先
			if (authorityList.length > 0 && !authorityList.some((v: string) => v === binding.value)) {
				el.parentNode?.removeChild(el)
			}
		}
	})
}
