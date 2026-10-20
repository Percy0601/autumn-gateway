import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import { directive } from './utils/directive'
import { router } from './router'
import { i18n } from './i18n'
import 'virtual:svg-icons-register'

import '@/icons/iconfont/iconfont'
import 'element-plus/dist/index.css'
import '@/styles/index.scss'

import MaComponent from '@/components/index'

const app = createApp(App)

// 全局错误兜底：异步 chunk 加载失败 / 渲染期异常时至少留下可诊断的日志，
// 不再静默白屏（原来这类异常会被 Vue 吞掉，页面上什么都看不到）
app.config.errorHandler = (err, _instance, info) => {
	console.error('[app] 未捕获异常：', info, err)
}

app.use(createPinia())

// element plus图标 全局注册
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
	app.component(key, component)
}

// 路由导航异常兜底（例如动态 import 的 chunk 404）
router.onError((error) => {
	console.error('[router] 路由导航异常：', error)
})

// 注册 自定义指令
directive(app)
app.use(i18n)
app.use(router)
app.use(MaComponent)
app.use(ElementPlus)

// 等首次路由解析完成再挂载：避免首屏先渲染一次空白/未匹配状态。
// 注意：首次导航失败（守卫抛异常、懒加载 chunk 404 —— 正是本次事故的同类场景）
// 时 isReady() 会 reject，若只用 .then() 则 app.mount 永不执行 → 整页彻底空白，
// 连上面的 errorHandler 都收不到。所以 catch 之后用 finally 无条件挂载。
router
	.isReady()
	.catch((error) => {
		console.error('[router] 首次导航失败：', error)
	})
	.finally(() => {
		app.mount('#app')
	})
