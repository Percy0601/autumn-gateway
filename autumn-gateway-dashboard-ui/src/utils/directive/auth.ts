import type { App } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * 按钮级权限指令：v-auth="'system:user:create'"
 *
 * 权限清单来源：后端 /api/currentUser 下发的 authorityList，
 * 由 store/modules/user 提到 store 顶层（见 getUserInfoAction）。
 *
 * 两条放行规则：
 *   1. 清单里含 "*"（超级管理员）—— 全放行；
 *   2. 清单里含传入的权限码 —— 放行。
 *
 * 兜底策略：拿不到清单（未登录 / 接口异常）时**不隐藏**元素。
 * 宁可多显示一个按钮让后端 403 兜住，也不要因为前端误判把功能整个藏起来
 * ——后者排查起来要翻前端代码，前者看接口响应就知道。
 */
export function auth(app: App) {
	app.directive('auth', {
		mounted(el, binding) {
			const userStore = useUserStore()
			const authorityList: string[] = userStore.authorityList || []

			// 没有权限清单时不隐藏任何元素（可用优先）
			if (authorityList.length === 0) return

			const code = binding.value
			if (!code) return

			const granted =
				authorityList.includes('*') || authorityList.some((v: string) => v === code)
			if (!granted) {
				el.parentNode?.removeChild(el)
			}
		}
	})
}
