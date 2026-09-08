import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { fileURLToPath, URL } from 'node:url'

/**
 * 构建产物直接输出到授权服务器的 static 目录，
 * 后端启动即可访问登录页，无需任何 Java 侧构建插件（不影响 graalvm native image）。
 */
export default defineConfig({
  plugins: [vue(), UnoCSS()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:9000',
      '/login': 'http://localhost:9000',
      '/oauth2': 'http://localhost:9000',
      '/userinfo': 'http://localhost:9000',
    },
  },
  build: {
    outDir: '../autumn-gateway-authorization/src/main/resources/static',
    emptyOutDir: true,
    assetsDir: 'assets',
  },
})
