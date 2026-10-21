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
					<p class="card-subtitle">本系统采用统一认证，点击下方按钮进入</p>
				</div>

				<div class="sso-block">
					<div class="sso-flow">
						<span class="sso-node">浏览器</span>
						<span class="sso-arrow">→</span>
						<span class="sso-node">网关 :8080</span>
						<span class="sso-arrow">→</span>
						<span class="sso-node sso-node-primary">授权服务器 :9000</span>
					</div>
					<el-button type="primary" class="login-btn" :loading="loading" @click="handleSsoLogin">
						使用统一认证登录
					</el-button>
					<p class="sso-hint">
						账号密码在授权服务器的登录页输入。令牌只保存在网关的服务端会话里，
						浏览器拿不到也存不下。
					</p>
				</div>

				<div class="login-tip">默认账号 admin / Autumn@123456</div>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)

/**
 * 统一认证登录：把浏览器送到网关的 OAuth2 授权入口。
 *
 * 旧流程是前端 POST /api/login/account 拿自签 JWT 存 localStorage —— 已废弃。
 * 现在令牌由 autumn-gateway-authorization 签发（RS256），网关作为 OAuth2 客户端
 * 完成授权码 + PKCE 流程，令牌留在网关会话，再由 TokenRelay 注入转发给后端。
 */
const handleSsoLogin = () => {
	loading.value = true
	userStore.redirectToLogin()
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
		letter-spacing: 2px;
		border-radius: 6px;
	}
}

.sso-block {
	.sso-flow {
		display: flex;
		align-items: center;
		justify-content: center;
		flex-wrap: wrap;
		gap: 6px;
		margin-bottom: 24px;
		font-size: 12px;
		color: #6b7280;

		.sso-node {
			padding: 4px 8px;
			border: 1px solid #e5e7eb;
			border-radius: 6px;
			background: #f9fafb;
			white-space: nowrap;
		}

		.sso-node-primary {
			color: #409eff;
			border-color: #b3d8ff;
			background: #ecf5ff;
		}

		.sso-arrow {
			color: #c0c4cc;
		}
	}

	.sso-hint {
		margin: 14px 0 0;
		font-size: 12px;
		line-height: 1.7;
		color: #909399;
	}
}

.login-tip {
	margin-top: 20px;
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
