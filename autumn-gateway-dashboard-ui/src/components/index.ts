import type { App, Plugin } from 'vue'
import MaDictColumn from '@/components/ma-dict/ma-dict-column/index.vue'
import MaDictLabel from '@/components/ma-dict/ma-dict-label/index.vue'
import MaDictRadio from '@/components/ma-dict/ma-dict-radio/index.vue'
import MaDictTreeSelect from '@/components/ma-dict/ma-dict-tree-select/index.vue'
import MaDictSelect from '@/components/ma-dict/ma-dict-select/index.vue'
import MaDictCheckbox from '@/components/ma-dict/ma-dict-checkbox/index.vue'
import MaDataColumn from '@/components/ma-data/ma-data-column/index.vue'
import MaDataLabel from '@/components/ma-data/ma-data-label/index.vue'
import MaDataSelect from '@/components/ma-data/ma-data-select/index.vue'
import MaDataTreeSelect from '@/components/ma-data/ma-data-tree-select/index.vue'
import MaDataTreeLeft from '@/components/ma-data/ma-data-tree-left/index.vue'
import MaDataTable from '@/components/ma-data/ma-data-table/index.vue'
import MaImageColumn from '@/components/ma-image-column/index.vue'

import MaIcon from '@/components/ma-icon/index.vue'
import MaEditor from '@/components/ma-editor/index.vue'
import MaMarkdown from '@/components/ma-markdown/index.vue'
import MaAddress from '@/components/ma-address/index.vue'
import MaIconSelect from '@/components/ma-icon-select/index.vue'

const components = [
	MaEditor,
	MaMarkdown,
	MaAddress,
	MaIcon,
	MaIconSelect,
	MaDictLabel,
	MaDictColumn,
	MaDictRadio,
	MaDictSelect,
	MaDictCheckbox,
	MaDictTreeSelect,
	MaDataColumn,
	MaDataLabel,
	MaDataSelect,
	MaDataTreeSelect,
	MaDataTreeLeft,
	MaDataTable,
	MaImageColumn
]

const MaComponent: Plugin = {
	install(Vue: App) {
		components.forEach((component: any) => {
			Vue.component(component.name, component)
		})
	}
}

export default MaComponent
