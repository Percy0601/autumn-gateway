declare module '*.svg'
declare module '*.png'
declare module '*.jpg'
declare module '*.gif'
declare module '*.scss'
declare module '*.ts'
declare module '*.js'

declare module '*.vue' {
	import type { DefineComponent } from 'vue'
	const component: DefineComponent<{}, {}, any>
	export default component
}

declare module '@kangc/v-md-editor'
declare module '@kangc/v-md-editor/lib/theme/github.js'

declare module '@wangeditor/editor-for-vue' {
	import { DefineComponent } from 'vue'
	export const Editor: DefineComponent<any, any, any>
	export const Toolbar: DefineComponent<any, any, any>
}
