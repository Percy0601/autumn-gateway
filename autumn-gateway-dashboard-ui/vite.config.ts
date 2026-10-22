import { resolve } from 'path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import WindiCSS from 'vite-plugin-windicss'

import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import vueSetupExtend from 'vite-plugin-vue-setup-extend'

// 参考：https://cn.vitejs.dev/config/
export default defineConfig({
	// 站点根路径部署（经网关访问 http://localhost:8080/）。
	//
	// 原来用 './'（相对路径），好处是可以塞进任意子目录，代价是深链刷新会崩：
	// 在 /system/user 上 F5，浏览器按相对规则把 ./assets/index-xxx.js 解析成
	// /system/assets/index-xxx.js → 404，且 utils/request.ts 推导出的 API 前缀
	// 也会变成 /system，接口全部 404。网关部署形态下站点就在根，用 '/' 才是对的。
	base: '/',
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
			},
			'/gateway': {
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
