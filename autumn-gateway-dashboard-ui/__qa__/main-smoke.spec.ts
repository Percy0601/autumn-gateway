// @vitest-environment jsdom
// QA 独立验证 —— 真实 main.ts 挂载路径（覆盖 D5/R3：router.isReady().catch().then(mount)）
// 这是 K 系列（自建 createRouter）覆盖不到的唯一盲区。
import { describe, it, expect, vi, beforeAll } from 'vitest'

// 1) 虚拟模块桩（vite 插件产物，测试环境不存在）
vi.mock('virtual:svg-icons-register', () => ({}))

// 2) 后端交互全部打桩，避免真实 axios
vi.mock('@/utils/request', () => {
	const get = (url: string) => {
		if (url === '/api/currentUser') {
			return Promise.reject(Object.assign(new Error('401'), { response: { status: 401 } }))
		}
		return Promise.reject(Object.assign(new Error('401'), { response: { status: 401 } }))
	}
	return {
		default: { get, post: () => Promise.reject(new Error('no backend')) },
		service: { get, post: () => Promise.reject(new Error('no backend')) }
	}
})

beforeAll(() => {
	// 3) jsdom 缺失的浏览器 API 桩
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
})

describe('M. 真实 main.ts 挂载 smoke', () => {
	it('M1 import main.ts 后 #app 必须被挂载出内容（不白屏）', async () => {
		await import('@/main')
		// 等 router.isReady() 的微任务 + Vue 首次渲染
		await vi.waitFor(
			() => {
				const el = document.getElementById('app')
				expect(el).toBeTruthy()
				expect(el!.children.length).toBeGreaterThan(0)
			},
			{ timeout: 15000 }
		)
		const el = document.getElementById('app')!
		const text = (el.textContent || '').replace(/\s+/g, ' ')
		console.log('[M1] #app children =', el.children.length, '| innerHTML 长度 =', el.innerHTML.length)
		console.log('[M1] 可见文本 =', text.slice(0, 160))
		// 无 token 时应落到登录页，页面上必须有登录相关文案
		expect(text.length).toBeGreaterThan(0)
		expect(text).toMatch(/登录|Autumn/)
	}, 30000)
})
