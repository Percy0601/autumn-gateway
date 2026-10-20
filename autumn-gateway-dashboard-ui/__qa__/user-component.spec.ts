// @vitest-environment jsdom
// QA 回归 —— User.vue 删掉 /profile 入口后仍能正常渲染（C 项要求）
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'

vi.mock('@/utils/request', () => {
	const get = () => Promise.resolve({ code: 200, data: [] })
	return {
		default: { get, post: () => Promise.resolve({ success: true, data: {} }) },
		service: { get, post: () => Promise.resolve({ success: true, data: {} }) }
	}
})

import UserComp from '@/layout/components/Navbar/components/User.vue'
import { useUserStore } from '@/store/modules/user'
// User.vue 模板里用了 $t('app.signOut')，真实应用在 main.ts 里 app.use(i18n)。
// 测试里必须同样装上 i18n 插件，否则 $t 在渲染期是 undefined →
// TypeError: _ctx.$t is not a function（这是测试环境缺失，不是组件缺陷）。
import { i18n } from '@/i18n'

describe('O. User.vue 渲染回归（D7 删 /profile 入口后）', () => {
	let router: any
	let pinia: any

	beforeEach(() => {
		pinia = createPinia()
		setActivePinia(pinia)
		router = createRouter({
			history: createMemoryHistory(),
			routes: [
				{ path: '/', component: { render: () => null } },
				{ path: '/login', component: { render: () => null } }
			]
		})
		// el-dropdown 的内容是 teleport 到 body 的 popper 容器，不清理会跨用例累积
		document.body.innerHTML = ''
	})

	it('O1 能渲染出 displayName，且不再包含 /profile 入口', async () => {
		const userStore = useUserStore()
		userStore.currentUser = { name: '张三', userid: '1' }
		userStore.user.name = '张三'

		const wrapper = mount(UserComp as any, {
			global: { plugins: [pinia, router, i18n, ElementPlus] }
		})
		const html = wrapper.html()
		const text = wrapper.text()
		// 关键：el-dropdown 的下拉内容走 teleport 渲染到 body 的 popper 容器
		// （#el-popper-container-*，初次渲染即存在、display:none），
		// 因此 wrapper.html() / wrapper.text() 里根本看不到下拉项。
		// 只断言 wrapper.html() 不含 /profile 是**空断言** —— 即使入口加回来也会绿。
		// 要验证「/profile 入口确实没了」「app.signOut 确实命中语言包」，必须查 body。
		const bodyHtml = document.body.innerHTML

		console.log('[O1] wrapper.text =', JSON.stringify(text))
		console.log('[O1] wrapper.html 含 dropdown?', /user-dropdown/.test(html))
		console.log('[O1] body 含 dropdown?', /user-dropdown/.test(bodyHtml), ' 含 profile?', /\/profile/.test(bodyHtml))

		// 1) 组件确实渲染出了内容（没有因删元素导致模板/引用错误）
		expect(wrapper.html().length).toBeGreaterThan(0)
		// 2) displayName getter 生效
		expect(text).toContain('张三')
		// 3) 下拉容器确实渲染出来了（否则下面「不含 /profile」又会变成空断言）
		expect(bodyHtml).toContain('user-dropdown')
		// 4) app.signOut 命中语言包：既不是回退成 key，也不是空（zh-CN.ts:18 app / :30 '退出'）
		expect(bodyHtml).toContain('退出')
		expect(bodyHtml).not.toContain('app.signOut')
		// 5) /profile 入口已彻底移除（含 teleport 出去的下拉内容）
		//    注意不能直接对 bodyHtml 做 /\/profile/ —— User.vue 里保留了一行 HTML 注释
		//    「暂不提供个人中心页（/profile 路由不存在…）」，注释不是入口。
		//    所以先剥掉注释，再断言；并额外断言下拉里一个 <a> 都没有（最硬的口径）。
		const bodyNoComment = bodyHtml.replace(/<!--[\s\S]*?-->/g, '')
		expect(bodyNoComment).not.toContain('/profile')
		expect(document.querySelector('a[href="/profile"]')).toBeNull()
		expect(document.querySelector('.user-dropdown a')).toBeNull()
		wrapper.unmount()
	})

	it('O2 currentUser 为空时也不崩（displayName 兜底「未登录」）', () => {
		const wrapper = mount(UserComp as any, {
			global: { plugins: [pinia, router, i18n, ElementPlus] }
		})
		console.log('[O2] 渲染文本 =', JSON.stringify(wrapper.text()))
		expect(wrapper.html().length).toBeGreaterThan(0)
		wrapper.unmount()
	})
})
