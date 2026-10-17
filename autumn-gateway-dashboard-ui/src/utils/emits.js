import mitt from 'mitt'

// 由 src/utils/emits.ts 转译生成，保持与 .ts 同步（Vite 优先解析 .js）
export default mitt()

export const MittEvent = {
	// 左侧菜单树
	LeftTree: 'leftTree'
}
