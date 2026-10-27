<script setup>
// 模板驱动的参数表单：根据 args_schema_json 动态渲染各类型控件。
// schema 字段：key / label / type(string|int|long|bool|enum|list|textarea)
//             list 多值：type='list' + itemType(string|int|enum) + itemOptions(enum 候选项)
//             required / default / options / placeholder
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

// ==================== list 多值支持 ====================
function listVal(f) {
  const v = valueOf(f)
  if (Array.isArray(v)) return v.filter((x) => x !== '' && x !== null && x !== undefined)
  return []
}
// 展示用：至少保留一行输入
function listRows(f) {
  const arr = listVal(f)
  return arr.length ? arr : ['']
}
function setList(f, arr) {
  const cleaned = arr.filter((x) => x !== '' && x !== null && x !== undefined)
  setField(f.key, cleaned.length ? cleaned : undefined)
}
function addRow(f) {
  setList(f, [...listVal(f), ''])
}
function updateRow(f, i, val) {
  const arr = [...listVal(f)]
  arr[i] = val
  setList(f, arr)
}
function removeRow(f, i) {
  const arr = [...listVal(f)]
  arr.splice(i, 1)
  setList(f, arr)
}
function itemCtl(f) {
  if (f.itemType === 'int') return 'int'
  if (f.itemType === 'enum') return 'enum'
  return 'string'
}
</script>

<template>
  <template v-for="f in schema" :key="f.key">
    <!-- list 多值：动态行增删 -->
    <el-form-item v-if="f.type === 'list'" :label="f.label || f.key" :required="!!f.required">
      <div class="list-box">
        <div v-for="(v, i) in listRows(f)" :key="i" class="list-row">
          <el-input
            v-if="itemCtl(f) === 'string'"
            :model-value="v"
            :placeholder="f.placeholder || ''"
            clearable
            @update:model-value="updateRow(f, i, $event)"
          />
          <el-input-number
            v-else-if="itemCtl(f) === 'int'"
            :model-value="v"
            :min="0"
            :placeholder="f.placeholder || ''"
            @update:model-value="updateRow(f, i, $event)"
          />
          <el-select
            v-else
            :model-value="v"
            :placeholder="f.placeholder || ''"
            @update:model-value="updateRow(f, i, $event)"
          >
            <el-option v-for="o in (f.itemOptions || [])" :key="o" :label="o" :value="o" />
          </el-select>
          <el-button class="row-rm" size="small" type="danger" text @click="removeRow(f, i)">删除</el-button>
        </div>
        <el-button size="small" class="row-add" @click="addRow(f)">＋ 添加一行</el-button>
      </div>
    </el-form-item>

    <!-- 单键值：string / 数字 / 枚举 / 开关 / 大文本 -->
    <el-form-item v-else :label="f.label || f.key" :required="!!f.required">
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
        v-else-if="f.type === 'textarea'"
        :model-value="valueOf(f)"
        @update:model-value="setField(f.key, $event)"
        type="textarea"
        :rows="3"
        :placeholder="f.placeholder || ''"
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

<style scoped>
.list-box {
  width: 100%;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 8px;
  background: #fafbfc;
}
.list-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.list-row :deep(.el-input),
.list-row :deep(.el-select),
.list-row :deep(.el-input-number) {
  flex: 1;
}
.row-rm {
  flex: 0 0 auto;
}
</style>
