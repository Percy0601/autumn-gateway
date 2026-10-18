import { resolve } from 'path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import WindiCSS from 'vite-plugin-windicss'

import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import vueSetupExtend from 'vite-plugin-vue-setup-extend'

// 参考：https://cn.vitejs.dev/config/
export default defineConfig({
	// 相对路径：独立部署或挂在网关子路径下都能正确加载静态资源
	base: './',
	resolve: {
		// 配置别名
		alias: {
			'@': resolve(__dirname, './src'),
			'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js'
		}
	},
	plugins: [
		vue(),
		vueSetupExtend(),
		WindiCSS(),
		createSvgIconsPlugin({
			iconDirs: [resolve(__dirname, 'src/icons/svg')],
			symbolId: 'icon-[dir]-[name]'
		})
	],
	server: {
		host: '0.0.0.0',
		port: 3000, // 端口号
		open: false, // 是否自动打开浏览器
		// 开发环境将 /api 代理到本地 dashboard 服务
		proxy: {
			'/api': {
				target: 'http://localhost:8080',
				changeOrigin: true
			}
		}
	},
	build: {
		// 拆分大依赖，避免单 chunk 过大（element-plus / vue 生态单独成块，利于缓存）
		rollupOptions: {
			output: {
				manualChunks: {
					vue: ['vue', 'vue-router', 'pinia'],
					element: ['element-plus', '@element-plus/icons-vue']
				}
			}
		},
		chunkSizeWarningLimit: 1600
	}
})
