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

// ==================== map 复杂类型（如 cors-configuration） ====================
// flat=true：该 map 的键值直接并入外层 args（SCG 的 Cors 过滤器 args 本身就是 map）
function mapTarget(f) {
  if (!model.value) model.value = {}
  if (f.flat) return model.value
  if (!model.value[f.key] || typeof model.value[f.key] !== 'object' || Array.isArray(model.value[f.key])) {
    model.value = { ...model.value, [f.key]: {} }
  }
  return model.value[f.key]
}
function mapEntryKeys(f) {
  return Object.keys(mapTarget(f))
}
function valTypeOf(v) {
  if (Array.isArray(v)) return 'list'
  if (typeof v === 'boolean') return 'bool'
  if (typeof v === 'number') return 'int'
  return 'string'
}
function convertVal(v, to) {
  if (to === 'list') return Array.isArray(v) ? v.slice() : (v === '' || v == null ? [] : [v])
  if (to === 'bool') return !!v
  if (to === 'int') return Number(v) || 0
  return v == null ? '' : String(v)
}
function setMapVal(f, key, val) {
  mapTarget(f)[key] = val
}
function setMapKey(f, oldKey, newKey) {
  if (!newKey || oldKey === newKey) return
  const t = mapTarget(f)
  const v = t[oldKey]
  delete t[oldKey]
  t[newKey] = v
}
function addMapEntry(f) {
  const t = mapTarget(f)
  const def = f.valueType === 'list' ? [] : (f.valueType === 'bool' ? false : (f.valueType === 'int' ? 0 : ''))
  let k = 'newKey'
  let i = 1
  while (k in t) k = 'newKey' + i++
  t[k] = def
}
function removeMapEntry(f, key) {
  delete mapTarget(f)[key]
}
function mapListVal(f, key) {
  const v = mapTarget(f)[key]
  return Array.isArray(v) ? v.filter((x) => x !== '' && x != null) : []
}
function setMapList(f, key, arr) {
  const cleaned = (arr || []).filter((x) => x !== '' && x != null)
  setMapVal(f, key, cleaned.length ? cleaned : [])
}
function updateMapListRow(f, key, idx, nv) {
  const arr = [...mapListVal(f, key)]
  arr[idx] = nv
  setMapList(f, key, arr)
}
function removeMapListRow(f, key, idx) {
  const arr = [...mapListVal(f, key)]
  arr.splice(idx, 1)
  setMapList(f, key, arr)
}
function addMapListRow(f, key) {
  setMapList(f, key, [...mapListVal(f, key), ''])
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

    <!-- map 复杂类型：键值可增删，值支持 文本/数字/开关/列表（如 cors-configuration） -->
    <el-form-item v-else-if="f.type === 'map'" :label="f.label || f.key">
      <div class="map-box">
        <div v-if="f.desc" class="map-desc">{{ f.desc }}</div>
        <div v-for="key in mapEntryKeys(f)" :key="key" class="map-row">
          <el-select v-if="f.keyOptions && f.keyOptions.length"
                     :model-value="key" filterable allow-create default-first-option class="map-key"
                     @update:model-value="(nv) => setMapKey(f, key, nv)">
            <el-option v-for="o in f.keyOptions" :key="o" :label="o" :value="o" />
          </el-select>
          <el-input v-else :model-value="key" class="map-key"
                    @update:model-value="(nv) => setMapKey(f, key, nv)" />
          <el-select :model-value="valTypeOf(mapTarget(f)[key])" class="map-vtype"
                     @update:model-value="(nv) => setMapVal(f, key, convertVal(mapTarget(f)[key], nv))">
            <el-option label="文本" value="string" />
            <el-option label="数字" value="int" />
            <el-option label="开关" value="bool" />
            <el-option label="列表" value="list" />
          </el-select>
          <template v-if="valTypeOf(mapTarget(f)[key]) === 'list'">
            <div class="map-list">
              <div v-for="(iv, idx) in mapListVal(f, key)" :key="idx" class="map-list-row">
                <el-input v-if="(f.itemType || 'string') === 'string'" :model-value="iv"
                          @update:model-value="updateMapListRow(f, key, idx, $event)" />
                <el-input-number v-else :model-value="iv" :min="0"
                                @update:model-value="updateMapListRow(f, key, idx, $event)" />
                <el-button size="small" type="danger" text @click="removeMapListRow(f, key, idx)">删</el-button>
              </div>
              <el-button size="small" @click="addMapListRow(f, key)">＋ 添加</el-button>
            </div>
          </template>
          <el-input v-else-if="valTypeOf(mapTarget(f)[key]) === 'string'" :model-value="mapTarget(f)[key]"
                    @update:model-value="(nv) => setMapVal(f, key, nv)" />
          <el-input-number v-else-if="valTypeOf(mapTarget(f)[key]) === 'int'" :model-value="mapTarget(f)[key]" :min="0"
                           @update:model-value="(nv) => setMapVal(f, key, nv)" />
          <el-switch v-else-if="valTypeOf(mapTarget(f)[key]) === 'bool'" :model-value="!!mapTarget(f)[key]"
                     @update:model-value="(nv) => setMapVal(f, key, nv)" />
          <el-button size="small" type="danger" text class="map-rm" @click="removeMapEntry(f, key)">移除</el-button>
        </div>
        <el-button size="small" class="map-add" @click="addMapEntry(f)">＋ 添加配置项</el-button>
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
.map-box {
  width: 100%;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 8px;
  background: #fafbfc;
}
.map-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.map-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.map-key {
  flex: 0 0 200px;
}
.map-vtype {
  flex: 0 0 90px;
}
.map-list {
  flex: 1;
  border-left: 2px solid #ebeef5;
  padding-left: 8px;
}
.map-list-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.map-list-row :deep(.el-input),
.map-list-row :deep(.el-input-number) {
  flex: 1;
}
.map-add {
  margin-top: 2px;
}
</style>
