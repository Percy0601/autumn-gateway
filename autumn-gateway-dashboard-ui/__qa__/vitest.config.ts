import { resolve } from 'path'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
	plugins: [vue()],
	root: resolve(__dirname, '..'),
	resolve: {
		alias: {
			'@': resolve(__dirname, '../src'),
			'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js',
			// 该虚拟模块由 vite-plugin-svg-icons 在构建期注入，测试环境用桩替代
			'virtual:svg-icons-register': resolve(__dirname, './stubs/svg-icons-register.ts')
		}
	},
	test: {
		environment: 'jsdom',
		include: ['__qa__/**/*.spec.ts'],
		globals: false,
		testTimeout: 30000,
		hookTimeout: 30000
	}
})
