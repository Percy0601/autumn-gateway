// @vitest-environment jsdom
// QA 独立验证 —— main.ts 的「首次导航 reject」路径（C 项要求）
// 场景：登录页 chunk 加载失败（懒加载 import reject）→ router.isReady() reject
// 期望：.catch() 记日志后 .finally() 仍然 mount，#app 必须被 Vue 接管（不白屏）
import { describe, it, expect, vi, beforeAll } from 'vitest'

vi.mock('virtual:svg-icons-register', () => ({}))

// 登录页组件「加载失败」—— 模拟 chunk 404
vi.mock('@/views/login/index.vue', () => {
	throw new Error('Failed to fetch dynamically imported module: login chunk 404')
})

vi.mock('@/utils/request', () => {
	const get = () => Promise.reject(Object.assign(new Error('401'), { response: { status: 401 } }))
	return {
		default: { get, post: () => Promise.reject(new Error('no backend')) },
		service: { get, post: () => Promise.reject(new Error('no backend')) }
	}
})

let errorSpy: any

beforeAll(() => {
	document.body.innerHTML = '<div id="app"></div><script data-qa="stub"></script>'
	Object.defineProperty(window, 'matchMedia', {
		writable: true,
		value: (query: string) => ({
			matches: false,
			media: query,
			onchange: null,
			addListener: () => {},
			removeListener: () => {},
			addEventListener: () => {},
			removeEventListener: () => {},
			dispatchEvent: () => false
		})
	})
	;(globalThis as any).ResizeObserver = class {
		observe() {}
		unobserve() {}
		disconnect() {}
	}
	errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('N. 首次导航失败的挂载兜底（D5/R3）', () => {
	it('N1 首次导航 reject 时 app.mount 仍必须执行', async () => {
		await import('@/main')

		await vi.waitFor(
			() => {
				const el = document.getElementById('app') as any
				expect(el && el.__vue_app__).toBeTruthy()
			},
			{ timeout: 15000 }
		)

		const el = document.getElementById('app') as any
		const calls = errorSpy.mock.calls.map((c: any[]) => String(c[0]))
		console.log('[N1] #app.__vue_app__ =', !!el.__vue_app__)
		console.log('[N1] 是否记录「首次导航失败」 =', calls.some((s: string) => s.indexOf('首次导航失败') > -1))
		console.log('[N1] console.error 首行 =', calls.slice(0, 4).join(' | ').slice(0, 300))

		// 1) Vue 接管了 #app —— 说明 mount 真的执行了
		expect(el.__vue_app__).toBeTruthy()
		// 2) isReady 确实 reject 了（否则这条用例没有真正覆盖目标分支）
		expect(calls.some((s: string) => s.indexOf('首次导航失败') > -1)).toBe(true)
	}, 30000)
})
