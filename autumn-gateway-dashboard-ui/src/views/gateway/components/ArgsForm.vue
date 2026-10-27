<script setup>
// 模板驱动的参数表单：根据 args_schema_json 动态渲染各类型控件。
// schema 字段：key / label / type(string|int|long|bool|enum) / required / default / options / placeholder
const props = defineProps({
  schema: { type: Array, default: () => [] },
})
const model = defineModel({ type: Object, default: () => ({}) })

function valueOf(f) {
  return (model.value || {})[f.key]
}
function setField(key, val) {
  model.value = { ...(model.value || {}), [key]: val }
}
</script>

<template>
  <template v-for="f in schema" :key="f.key">
    <el-form-item :label="f.label || f.key" :required="!!f.required">
      <el-input
        v-if="f.type === 'string' || !f.type"
        :model-value="valueOf(f)"
        @update:model-value="setField(f.key, $event)"
        :placeholder="f.placeholder || ''"
        clearable
      />
      <el-input-number
        v-else-if="f.type === 'int' || f.type === 'long'"
        :model-value="valueOf(f)"
        @update:model-value="setField(f.key, $event)"
        :min="0"
      />
      <el-select
        v-else-if="f.type === 'enum'"
        :model-value="valueOf(f)"
        @update:model-value="setField(f.key, $event)"
        clearable
      >
        <el-option v-for="o in (f.options || [])" :key="o" :label="o" :value="o" />
      </el-select>
      <el-switch
        v-else-if="f.type === 'bool'"
        :model-value="!!valueOf(f)"
        @update:model-value="setField(f.key, $event)"
      />
      <el-input
        v-else
        :model-value="valueOf(f)"
        @update:model-value="setField(f.key, $event)"
        :placeholder="f.placeholder || ''"
      />
    </el-form-item>
  </template>
</template>
