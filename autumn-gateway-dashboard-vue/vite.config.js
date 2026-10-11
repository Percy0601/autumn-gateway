import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import WindiCSS from 'vite-plugin-windicss'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), WindiCSS()],
  // 相对路径：独立部署（/）或挂在网关子路径（如 /dashboard/）都能正确加载静态资源
  base: './',
  build: {
    // 直接编译进 dashboard 服务的静态资源目录，随 jar 一起发布
    outDir: '../autumn-gateway-dashboard/src/main/resources/static',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境将 /api 代理到本地 dashboard 服务
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
