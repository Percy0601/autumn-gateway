// @vitest-environment jsdom
// QA 独立回归验证 —— 登录守卫 / token 失效链路（疑点 1、疑点 2）
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { httpGet, httpPost } = vi.hoisted(() => ({ httpGet: vi.fn(), httpPost: vi.fn() }))
vi.mock('@/utils/request', () => ({
	default: { get: httpGet, post: httpPost },
	service: { get: httpGet, post: httpPost }
}))

import { useUserStore } from '@/store/modules/user'

const err401 = () => {
	const e: any = new Error('Request failed with status code 401')
	e.response = { status: 401, data: { success: false, data: { access: 'guest' } } }
	return e
}

beforeEach(() => {
	setActivePinia(createPinia())
	localStorage.clear()
	httpGet.mockReset()
	httpPost.mockReset()
})

describe('H. 疑点 1：getUserInfoAction 是否吞掉异常', () => {
	it('H1 /api/currentUser 401 时 getUserInfoAction 必须 reject（否则守卫 catch 是死代码）', async () => {
		httpGet.mockRejectedValue(err401())
		const userStore = useUserStore()
		let rejected = false
		try {
			await userStore.getUserInfoAction()
		} catch {
			rejected = true
		}
		console.log('[H1] getUserInfoAction rejected =', rejected, '| user.id =', JSON.stringify(userStore.user.id))
		expect(rejected).toBe(true)
	})
	// H1b 原为「吞异常」时期的现状记录，与 H1 互相矛盾（H1 要求 reject）。
	// D2 修复后行为契约已变（失败一律上抛），此处同步为「reject + 清空用户态」。
	it('H1b getUserInfoAction 401 时 reject 且 currentUser 被清空', async () => {
		httpGet.mockRejectedValue(err401())
		const userStore = useUserStore()
		let rejected = false
		try {
			await userStore.getUserInfoAction()
		} catch {
			rejected = true
		}
		expect(rejected).toBe(true)
		expect(userStore.currentUser).toBeNull()
	})
	it('H1c 接口 200 但没有 data 时返回 null（不抛，由守卫判空处理）', async () => {
		httpGet.mockResolvedValue({ success: true })
		const userStore = useUserStore()
		await expect(userStore.getUserInfoAction()).resolves.toBeNull()
		expect(userStore.currentUser).toBeNull()
	})
	it('H1d 成功时返回 currentUser 且 user.id = userid', async () => {
		httpGet.mockResolvedValue({ success: true, data: { name: 'admin', userid: '7' } })
		const userStore = useUserStore()
		const cu = await userStore.getUserInfoAction()
		expect(cu).toEqual({ name: 'admin', userid: '7' })
		expect(userStore.user.id).toBe('7')
	})
	// ---- 三态契约（工程师 Round 3 引入，见 user.ts:47-51 注释）----
	it('H1e 403 也必须抛异常（与 401 同档）', async () => {
		const e: any = new Error('403')
		e.response = { status: 403 }
		httpGet.mockRejectedValue(e)
		const userStore = useUserStore()
		let rejected = false
		try {
			await userStore.getUserInfoAction()
		} catch {
			rejected = true
		}
		expect(rejected).toBe(true)
		expect(userStore.currentUser).toBeNull()
	})
	it('H1f 网络抖动（无 response）必须返回 undefined —— 不能抛、不能返回 null', async () => {
		httpGet.mockRejectedValue(Object.assign(new Error('Network Error'), { response: undefined }))
		const userStore = useUserStore()
		const r = await userStore.getUserInfoAction()
		expect(r).toBeUndefined()
		expect(userStore.currentUser).toBeNull()
	})
	it('H1g 超时（status 非 401/403）同样返回 undefined', async () => {
		httpGet.mockRejectedValue(Object.assign(new Error('timeout'), { response: { status: 504 } }))
		const userStore = useUserStore()
		await expect(userStore.getUserInfoAction()).resolves.toBeUndefined()
	})
})

describe('I. 疑点 2：后端 /api/currentUser 字段名为 userid（已在 LoginController:98 确认）', () => {
	it('I1 正常返回时 user.id === data.userid', async () => {
		httpGet.mockResolvedValue({
			success: true,
			data: { name: 'admin', avatar: '', userid: '1', email: '', phone: '', access: 'admin' }
		})
		const userStore = useUserStore()
		await userStore.getUserInfoAction()
		expect(userStore.user.id).toBe('1')
		expect(userStore.currentUser?.userid).toBe('1')
	})
	it('I2 后端若改名为 userId / id 时的兼容行为', async () => {
		httpGet.mockResolvedValue({ success: true, data: { userid: undefined, id: 42 } })
		const userStore = useUserStore()
		await userStore.getUserInfoAction()
		expect(userStore.user.id).toBe(42)
	})
})

describe('J. 登录与退出', () => {
	it('J1 accountLoginAction 写入 token（后端返回 {status,token,userid}）', async () => {
		httpPost.mockResolvedValue({ status: 'ok', type: 'account', currentAuthority: 'admin', token: 'T123', userid: '1', name: 'admin' })
		const userStore = useUserStore()
		const body: any = await userStore.accountLoginAction({ username: 'admin', password: 'x' })
		expect(userStore.token).toBe('T123')
		expect(localStorage.getItem('token')).toBe('T123')
		expect(body.token).toBe('T123')
	})
	it('J2 退出后 token 清空', async () => {
		httpPost.mockResolvedValue({ status: 'ok', token: 'T1', userid: '1' })
		const userStore = useUserStore()
		await userStore.accountLoginAction({ username: 'a', password: 'b' })
		await userStore.logoutAction()
		expect(userStore.token).toBe('')
		expect(localStorage.getItem('token')).toBeNull()
	})
	it('J3 登录失败（后端返回 status=error，无 token）时不写 token', async () => {
		httpPost.mockResolvedValue({ status: 'error', type: 'account', currentAuthority: 'guest' })
		const userStore = useUserStore()
		await userStore.accountLoginAction({ username: 'a', password: 'b' })
		expect(userStore.token).toBe('')
		expect(localStorage.getItem('token')).toBeNull()
	})
})
