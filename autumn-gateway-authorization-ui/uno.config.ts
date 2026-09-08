import { defineConfig, presetUno, presetAttributify } from 'unocss'

/**
 * UnoCSS（Windi CSS 的继任者，语法基本兼容，仍在活跃维护）
 * 只在构建期生成样式，产物是纯 CSS，浏览器端零运行时开销。
 */
export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
  ],
  theme: {
    colors: {
      brand: {
        50: '#eef4ff',
        100: '#dbe6ff',
        400: '#6f8dfb',
        500: '#4a6cf7',
        600: '#3451d6',
        700: '#2940ab',
      },
    },
  },
})
