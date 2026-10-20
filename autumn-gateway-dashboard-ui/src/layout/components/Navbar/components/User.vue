<template>
	<el-dropdown class="avatar-container" trigger="hover">
		<div class="avatar-wrapper">
			<el-avatar shape="circle" :size="30" :src="userStore.user.avatar"></el-avatar>
			<span>{{ userStore.displayName }}</span>
			<el-icon class="el-icon--right"><ArrowDown /></el-icon>
		</div>
		<template #dropdown>
			<el-dropdown-menu class="user-dropdown">
				<!-- 暂不提供个人中心页（/profile 路由不存在，点了会跳 404），先隐藏入口 -->
				<el-dropdown-item divided @click="logout"> {{ $t('app.signOut') }} </el-dropdown-item>
			</el-dropdown-menu>
		</template>
	</el-dropdown>
</template>

<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()

const logout = () => {
	userStore.logoutAction().then(() => {
		// 用路由跳转代替 location.reload()：
		// 后端没有 SPA 回退，在 /dashboard/index 这类深链上 reload 会拿到
		// Spring Boot Whitelabel 404。退出时 token 已在 logoutAction 里清空，
		// 直接进登录页即可（守卫走 whiteList 分支放行）。
		router.push({ path: '/login' })
	})
}
</script>

<style lang="scss" scoped>
.avatar-container {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	height: var(--theme-header-height);
	.avatar-wrapper {
		display: flex;
		align-items: center;
		white-space: nowrap;
		cursor: pointer;
		padding: 0 8px;
		color: var(--theme-header-text-color);
		span {
			margin-left: 6px;
		}
	}
	//&:hover {
	//	background: var(--theme-header-hover-color);
	//}
}
</style>
