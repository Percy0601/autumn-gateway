<script setup>
// 配置项/类型字段「元数据」行编辑器（v2）：
// 模板配置项（gw_tpl_field）与复合类型结构（gw_value_type_field）共用。
// 每行卡片可编辑：key / label / 值类型 / 必填 / 默认值 / 枚举选项 / 占位 /
// 校验正则 / 敏感 / 是否 map（map 键正则）/ 导出键 / 备注；支持上下移与删除。
import { ref, watch } from 'vue'

const props = defineProps({
  // 行数组：{keyName,label,valueTypeId,required,defaultValue,optionsJson,placeholder,
  //          validator,sensitive,isMapKey,mapKeyValidator,exportKey,sortNum,remark,role?}
  fields: { type: Array, default: () => [] },
  // 值类型目录（用于选择）：{id,name,displayName,kind}
  types: { type: Array, default: () => [] },
  // LIST=仅允许 1 行 ITEM；OBJECT/模板=任意 FIELD 行
  listMode: { type: Boolean, default: false },
})
const emit = defineEmits(['update:fields'])

const rows = ref([])
watch(
  () => props.fields,
  (v) => {
    rows.value = (v || []).map((f) => ({ ...f }))
  },
  { immediate: true, deep: true }
)

const typeNameOf = (id) => {
  const t = props.types.find((x) => x.id === Number(id))
  return t ? `${t.displayName}（${t.name}）` : `#${id}`
}
const typeKindOf = (id) => props.types.find((x) => x.id === Number(id))?.kind || ''

const sync = () => emit('update:fields', rows.value.map((r) => ({ ...r })))

function addRow() {
  rows.value.push({
    keyName: '',
    label: '',
    valueTypeId: null,
    required: 0,
    defaultValue: '',
    optionsJson: '',
    placeholder: '',
    validator: '',
    sensitive: 0,
    isMapKey: 0,
    mapKeyValidator: '',
    exportKey: '',
    sortNum: rows.value.length + 1,
    remark: '',
    role: 'FIELD',
  })
  sync()
}
function removeRow(i) {
  rows.value.splice(i, 1)
  renumber()
}
function move(i, dir) {
  const j = i + dir
  if (j < 0 || j >= rows.value.length) return
  const tmp = rows.value[i]
  rows.value[i] = rows.value[j]
  rows.value[j] = tmp
  renumber()
}
function renumber() {
  rows.value.forEach((r, i) => {
    r.sortNum = i + 1
  })
  sync()
}
</script>

<template>
  <div class="field-rows-editor">
    <el-empty v-if="!rows.length" description="暂无配置项，点击下方添加" :image-size="60" />

    <div v-for="(r, i) in rows" :key="i" class="frow">
      <div class="frow-head">
        <el-button-group size="small">
          <el-button :disabled="i === 0" @click="move(i, -1)">↑</el-button>
          <el-button :disabled="i === rows.length - 1" @click="move(i, 1)">↓</el-button>
        </el-button-group>
        <span class="frow-title">{{ r.keyName || `配置项 ${i + 1}` }}</span>
        <span class="frow-role">
          {{ listMode ? '元素 ITEM' : r.role === 'ITEM' ? '元素' : '字段' }}
          <el-tag v-if="typeKindOf(r.valueTypeId)" size="small">{{ typeKindOf(r.valueTypeId) }}</el-tag>
        </span>
        <el-button size="small" type="danger" text :disabled="listMode" @click="removeRow(i)">删除</el-button>
      </div>

      <div class="frow-grid">
        <div class="fcell">
          <div class="fcell-label">Key（导出名）</div>
          <el-input v-model="r.keyName" size="small" :disabled="r.role === 'ITEM'" placeholder="如 name / retries" @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">显示名</div>
          <el-input v-model="r.label" size="small" placeholder="如 重试次数" @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">值类型</div>
          <el-select v-model="r.valueTypeId" size="small" filterable placeholder="选择值类型" class="w-full" @change="sync">
            <el-option v-for="t in types" :key="t.id" :label="`${t.displayName}（${t.name}）`" :value="t.id" />
          </el-select>
        </div>
        <div class="fcell fcell-switch">
          <div class="fcell-label">必填</div>
          <el-switch v-model="r.required" :active-value="1" :inactive-value="0" @change="sync" />
        </div>
        <div class="fcell fcell-switch">
          <div class="fcell-label">敏感</div>
          <el-switch v-model="r.sensitive" :active-value="1" :inactive-value="0" @change="sync" />
        </div>
        <div class="fcell fcell-switch">
          <div class="fcell-label">Map（键值）</div>
          <el-switch v-model="r.isMapKey" :active-value="1" :inactive-value="0" @change="sync" />
        </div>
      </div>

      <div class="frow-grid">
        <div class="fcell">
          <div class="fcell-label">默认值</div>
          <el-input v-model="r.defaultValue" size="small" placeholder="如 3 / 500ms" @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">选项 optionsJson（枚举用）</div>
          <el-input v-model="r.optionsJson" size="small" placeholder='["GET","POST"]' @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">占位提示</div>
          <el-input v-model="r.placeholder" size="small" placeholder="如 /api/**" @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">值校验正则</div>
          <el-input v-model="r.validator" size="small" placeholder="如 ^/[\\w*/{}.-]*$" @input="sync" />
        </div>
        <div v-if="r.isMapKey === 1" class="fcell">
          <div class="fcell-label">Map 键正则</div>
          <el-input v-model="r.mapKeyValidator" size="small" placeholder="如 ^/[\\w*/{}.-]*$" @input="sync" />
        </div>
        <div class="fcell">
          <div class="fcell-label">导出键（缺省=Key）</div>
          <el-input v-model="r.exportKey" size="small" placeholder="如 backoff.firstBackoff 中的 firstBackoff" @input="sync" />
        </div>
        <div class="fcell fcell-wide">
          <div class="fcell-label">备注</div>
          <el-input v-model="r.remark" size="small" placeholder="该配置项说明" @input="sync" />
        </div>
      </div>
    </div>

    <el-button v-if="!listMode" class="mt-2" @click="addRow">+ 添加配置项</el-button>
    <div v-else class="frow-tip">LIST 类型只允许 1 个元素定义（ITEM），元素的 key 固定为 ITEM</div>
  </div>
</template>

<style scoped>
.field-rows-editor {
  width: 100%;
}
.frow {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 10px;
  background: #fff;
}
.frow-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.frow-title {
  font-weight: 600;
  font-size: 13px;
}
.frow-role {
  flex: 1;
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.frow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}
.frow-grid:last-child {
  margin-bottom: 0;
}
.fcell {
  min-width: 0;
}
.fcell-wide {
  grid-column: span 2;
}
.fcell-switch {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fcell-label {
  color: #606266;
  font-size: 12px;
  margin-bottom: 2px;
}
.frow-tip {
  color: #909399;
  font-size: 12px;
}
</style>
