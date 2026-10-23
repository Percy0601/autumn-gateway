<template>
	<div class="login-container">
		<div class="bg-decoration">
			<div class="circle circle-1"></div>
			<div class="circle circle-2"></div>
			<div class="circle circle-3"></div>
		</div>

		<!-- 左侧品牌区 -->
		<div class="login-brand">
			<div class="brand-content">
				<div class="brand-logo">
					<div class="logo-icon">
						<svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
							<path d="M24 4L6 14v20l18 10 18-10V14L24 4z" stroke="currentColor" stroke-width="2" fill="none" />
							<path d="M24 4v20m0 0l18-10m-18 10L6 14m18 30V24" stroke="currentColor" stroke-width="2" />
							<circle cx="24" cy="24" r="6" fill="currentColor" opacity="0.3" />
						</svg>
					</div>
					<h1 class="brand-title">Autumn Gateway</h1>
				</div>
				<p class="brand-slogan">网关管理后台 · 统一管控平台</p>
				<div class="brand-desc">
					基于 maku-admin 全新重构，涵盖应用、用户、角色、权限与资源管理能力，
					交互更顺滑、界面更精致。
				</div>
				<div class="brand-features">
					<div class="feature-item">
						<div class="feature-icon">
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
							</svg>
						</div>
						<span>安全可靠</span>
					</div>
					<div class="feature-item">
						<div class="feature-icon">
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<circle cx="12" cy="12" r="10" />
								<polyline points="12 6 12 12 16 14" />
							</svg>
						</div>
						<span>统一管控</span>
					</div>
					<div class="feature-item">
						<div class="feature-icon">
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
								<line x1="3" y1="9" x2="21" y2="9" />
								<line x1="9" y1="21" x2="9" y2="9" />
							</svg>
						</div>
						<span>灵活扩展</span>
					</div>
				</div>
			</div>
		</div>

		<!-- 右侧登录卡片 -->
		<div class="login-panel">
			<div class="login-card">
				<div class="card-header">
					<h2 class="card-title">欢迎使用</h2>
					<p class="card-subtitle">请登录您的账户以继续使用</p>
				</div>

				<el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleSubmit">
					<el-form-item prop="username">
						<el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" clearable size="large" />
					</el-form-item>
					<el-form-item prop="password">
						<el-input
							v-model="form.password"
							type="password"
							placeholder="请输入密码"
							:prefix-icon="Lock"
							show-password
							size="large"
						/>
					</el-form-item>
					<el-form-item prop="captcha">
						<div class="captcha-row">
							<el-input v-model="form.captcha" placeholder="请输入验证码" size="large" clearable />
							<img
								v-if="captchaImg"
								:src="captchaImg"
								class="captcha-img"
								alt="验证码"
								title="点击刷新验证码"
								@click="refreshCaptcha"
								@load="onCaptchaImgLoad"
								@error="onCaptchaImgError"
							/>
							<el-button
								v-else
								class="captcha-img"
								size="large"
								:type="captchaError ? 'danger' : 'default'"
								:loading="captchaLoading"
								@click="refreshCaptcha"
							>
								{{ captchaLoading ? '' : captchaError ? '重试' : '获取验证码' }}
							</el-button>
						</div>
						<p v-if="captchaError" class="captcha-error">验证码加载失败，请点击右侧按钮重试</p>
					</el-form-item>
					<el-form-item>
						<div class="login-options">
							<el-checkbox v-model="autoLogin">自动登录</el-checkbox>
						</div>
					</el-form-item>
					<el-form-item>
						<el-button type="primary" class="login-btn" :loading="loading" @click="handleSubmit">
							登 录
						</el-button>
					</el-form-item>
				</el-form>

				<div class="login-tip">默认账号 admin / Autumn@123456</div>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const autoLogin = ref(true)

const form = reactive({
	username: '',
	password: '',
	captcha: ''
})

// 验证码：直出图片方案 —— GET /api/captcha 直接返回 SVG，<img src> 渲染，
// 点击刷新；captchaId 由 HttpOnly Cookie 下发，前端无需感知。
// 加载中/失败都有明确视觉状态——静默失败是「验证码不显示」类问题最难排查的原因。
const captchaImg = ref('')
const captchaLoading = ref(false)
const captchaError = ref(false)

// 刷新验证码：换 URL 触发新请求。?_t= 时间戳防浏览器缓存（后端已带 Cache-Control: no-store，双保险），
// 保证点击必然拿到新图 + 新 captchaId。
const refreshCaptcha = () => {
	captchaLoading.value = true
	captchaError.value = false
	captchaImg.value = `/api/captcha?_t=${Date.now()}`
	form.captcha = ''
}

// 图片加载成功：清除加载态
const onCaptchaImgLoad = () => {
	captchaLoading.value = false
}

// 图片加载失败（接口 404 / 后端未启动 / 网络异常）：退回按钮态，允许手动重试
const onCaptchaImgError = () => {
	captchaLoading.value = false
	captchaImg.value = ''
	captchaError.value = true
}

onMounted(refreshCaptcha)

const rules = {
	username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
	password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
	captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 登录：POST /gateway/endpoint/login（由网关 starter 完成校验并签发 JWT），
// 成功后 token 存 localStorage，后续请求统一带 Authorization: Bearer。
const handleSubmit = () => {
	formRef.value.validate(async (valid: boolean) => {
		if (!valid) return
		loading.value = true
		try {
			// 验证码一次性消费：无论成败都会刷新，避免复用与过期
			const body = await userStore.accountLoginAction({ ...form })
			if (body && body.status === 'ok') {
				ElMessage.success('登录成功')
				// 这里只是预热用户态：登录本身已经成功，即便 /api/currentUser 暂时取不到
				// 也不能让异常冒泡到外层 catch —— 那会把流程误判成「登录失败」，
				// 用户会看到「登录成功」和「登录失败」自相矛盾的提示。
				// 真正的认证判定由路由守卫负责（守卫失败会清 token 并回登录页）。
				await userStore.fetchUserInfo().catch(() => undefined)
				const redirect = route.query.redirect
				const target =
					typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/dashboard/index'
				await router.replace(target)
				return
			}
			// 失败：优先展示服务端返回的具体原因（验证码错误 / 账号或密码错误 / 账号已禁用），
			// 连续失败达到上限账号会被锁定，此时必须由管理员重新启用
			await refreshCaptcha()
			ElMessageBox.alert(body?.message || '用户名或密码错误', '登录失败', { type: 'error' })
		} catch {
			await refreshCaptcha()
			ElMessage.error('登录失败，请稍后重试')
		} finally {
			loading.value = false
		}
	})
}
</script>

<style lang="scss" scoped>
.login-container {
	display: flex;
	min-height: 100vh;
	background: linear-gradient(135deg, #1e88e5 0%, #1565c0 45%, #0d47a1 100%);
	position: relative;
	overflow: hidden;
}

.bg-decoration {
	position: absolute;
	inset: 0;
	pointer-events: none;

	.circle {
		position: absolute;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.1);
	}

	.circle-1 {
		width: 420px;
		height: 420px;
		top: -120px;
		left: -100px;
	}

	.circle-2 {
		width: 320px;
		height: 320px;
		bottom: -80px;
		left: 28%;
	}

	.circle-3 {
		width: 220px;
		height: 220px;
		top: 40%;
		left: 18%;
	}
}

.login-brand {
	flex: 1;
	display: flex;
	flex-direction: column;
	justify-content: center;
	align-items: center;
	padding: 60px;
	color: #fff;
	position: relative;
	z-index: 1;

	.brand-content {
		max-width: 480px;
	}

	.brand-logo {
		display: flex;
		align-items: center;
		gap: 16px;
		margin-bottom: 16px;

		.logo-icon {
			width: 56px;
			height: 56px;
			background: rgba(255, 255, 255, 0.2);
			border-radius: 16px;
			display: flex;
			align-items: center;
			justify-content: center;
			backdrop-filter: blur(10px);

			svg {
				width: 32px;
				height: 32px;
				color: #fff;
			}
		}
	}

	.brand-title {
		font-size: 36px;
		font-weight: 700;
		letter-spacing: 2px;
		margin: 0;
	}

	.brand-slogan {
		font-size: 18px;
		opacity: 0.9;
		margin-bottom: 24px;
		font-weight: 300;
		letter-spacing: 4px;
	}

	.brand-desc {
		font-size: 14px;
		line-height: 1.8;
		opacity: 0.8;
		margin-bottom: 40px;
		padding: 20px;
		background: rgba(255, 255, 255, 0.1);
		border-radius: 12px;
		backdrop-filter: blur(10px);
		border: 1px solid rgba(255, 255, 255, 0.2);
	}

	.brand-features {
		display: flex;
		gap: 32px;

		.feature-item {
			display: flex;
			align-items: center;
			gap: 8px;
			font-size: 14px;
			opacity: 0.9;

			.feature-icon {
				width: 36px;
				height: 36px;
				background: rgba(255, 255, 255, 0.15);
				border-radius: 10px;
				display: flex;
				align-items: center;
				justify-content: center;

				svg {
					width: 18px;
					height: 18px;
				}
			}
		}
	}
}

.login-panel {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40px;
	position: relative;
	z-index: 1;
}

.login-card {
	width: 420px;
	background: #fff;
	border-radius: 24px;
	padding: 48px 40px;
	box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);

	.card-header {
		text-align: center;
		margin-bottom: 32px;

		.card-title {
			font-size: 28px;
			font-weight: 600;
			color: #1a1a2e;
			margin: 0 0 8px 0;
		}

		.card-subtitle {
			font-size: 14px;
			color: #8b8b9a;
			margin: 0;
		}
	}

	:deep(.el-button--primary) {
		width: 100%;
		height: 46px;
		font-size: 16px;
		font-weight: 600;
		letter-spacing: 4px;
		border-radius: 6px;
	}

	.login-options {
		width: 100%;
		display: flex;
		justify-content: space-between;
	}

	.captcha-row {
		width: 100%;
		display: flex;
		align-items: center;
		gap: 12px;

		.el-input {
			flex: 1;
		}
	}
}

.captcha-img {
	width: 120px;
	height: 40px;
	flex-shrink: 0;
	border-radius: 6px;
	border: 1px solid #dcdfe6;
	background: #fff;
	cursor: pointer;
	object-fit: cover;

	&:hover {
		border-color: #409eff;
	}
}

.captcha-error {
	margin: 4px 0 0;
	font-size: 12px;
	line-height: 1.4;
	color: #f56c6c;
}

.login-tip {
	margin-top: 16px;
	text-align: center;
	font-size: 13px;
	color: rgba(0, 0, 0, 0.45);
}

@media only screen and (max-width: 992px) {
	.login-brand {
		display: none;
	}

	.login-container {
		justify-content: center;
	}

	.login-panel {
		width: 100%;
		max-width: 480px;
	}
}
</style>
