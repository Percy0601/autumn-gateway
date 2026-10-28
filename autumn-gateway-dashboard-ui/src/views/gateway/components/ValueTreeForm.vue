<script setup>
// 递归「值」编辑器（v2）：由模板/类型树的 slot 驱动，支持
// BASIC（字符串/整数/浮点/布尔/时长/字节/枚举/日期/文本域/敏感）、
// LIST（动态行，元素可再为复合类型）、OBJECT（嵌套字段）、
// is_map_key=1（键值 Map：键按 map_key_validator 校验，值类型任意）。
//
// 两种用法：
//  1) 列表模式：modelValue=实例对象，slots=顶层配置项数组
//     → 每个槽的值读写 modelValue[slot.keyName]
//  2) 单槽模式：modelValue=单个值，slot=该配置项（含递归 valueType）
//     → 槽的值读写 modelValue 本身（map 值 / list 元素 / 嵌套对象内部用）
import { computed } from 'vue'

defineOptions({ name: 'ValueTreeForm' })

const props = defineProps({
  modelValue: { type: null, default: () => ({}) },
  slots: { type: Array, default: () => [] },
  slot: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])

const value = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const renderSlots = computed(() => (props.slot ? [props.slot] : props.slots))

// ---------- 类型工具 ----------
const vt = (slot) => slot?.valueType || {}
const isMap = (slot) => slot?.isMapKey === 1 || slot?.isMapKey === true
const fieldsOf = (type) => (type?.fields || []).filter((f) => f.role === 'FIELD' || !f.role)
const itemOf = (type) => (type?.fields || []).find((f) => f.role === 'ITEM')

function parseOptions(slot) {
  const raw = slot?.optionsJson || ''
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function basicInit(name, dv) {
  if (dv !== undefined && dv !== null && dv !== '') {
    if (name === 'INT' || name === 'LONG' || name === 'DOUBLE') return Number(dv)
    if (name === 'BOOL') return dv === true || dv === 'true'
    return String(dv)
  }
  if (name === 'BOOL') return false
  if (name === 'INT' || name === 'LONG' || name === 'DOUBLE') return undefined
  return ''
}

// 按 slot 生成初始值（新增 map 键 / list 行 / 新建实例时用）
function initValueFor(slot) {
  const t = vt(slot)
  if (isMap(slot)) return {}
  if (t.kind === 'LIST') return []
  if (t.kind === 'OBJECT') {
    const o = {}
    fieldsOf(t).forEach((f) => {
      o[f.keyName] = initValueFor(f)
    })
    return o
  }
  return basicInit(t.name, slot.defaultValue)
}

// ---------- 模式感知的读写（单槽模式读写 modelValue 本身） ----------
const slotValue = (slot) => (props.slot ? props.modelValue : props.modelValue?.[slot.keyName])
function setSlotValue(slot, v) {
  if (props.slot) emit('update:modelValue', v)
  else {
    props.modelValue[slot.keyName] = v
    touch()
  }
}

const mapObj = (slot) => {
  const o = props.slot ? props.modelValue : props.modelValue?.[slot.keyName]
  return o && typeof o === 'object' && !Array.isArray(o) ? o : null
}
const mapKeys = (slot) => {
  const o = mapObj(slot)
  return o ? Object.keys(o) : []
}
const mapValAt = (slot, idx) => {
  const o = mapObj(slot)
  return o ? o[Object.keys(o)[idx]] : undefined
}
function addMapKey(slot) {
  const o = mapObj(slot)
  if (!o) {
    if (props.slot) emit('update:modelValue', {})
    else props.modelValue[slot.keyName] = {}
  }
  mapObj(slot)[''] = initValueFor(slot)
  touch()
}
function removeMapKey(slot, idx) {
  const o = mapObj(slot)
  if (!o) return
  delete o[Object.keys(o)[idx]]
  touch()
}
function renameMapKey(slot, idx, newKey) {
  const o = mapObj(slot)
  if (!o) return
  const oldKey = Object.keys(o)[idx]
  if (!oldKey || oldKey === newKey) return
  const v = o[oldKey]
  delete o[oldKey]
  o[newKey] = v
  touch()
}
function setMapVal(slot, idx, v) {
  const o = mapObj(slot)
  if (!o) return
  o[Object.keys(o)[idx]] = v
  touch()
}

const listArr = (slot) => {
  const a = props.slot ? props.modelValue : props.modelValue?.[slot.keyName]
  return Array.isArray(a) ? a : []
}
const itemAt = (slot, idx) => listArr(slot)[idx]
function addListItem(slot) {
  if (!Array.isArray(props.slot ? props.modelValue : props.modelValue?.[slot.keyName])) {
    if (props.slot) emit('update:modelValue', [])
    else props.modelValue[slot.keyName] = []
  }
  listArr(slot).push(initValueFor(itemOf(vt(slot))))
  touch()
}
function removeListItem(slot, idx) {
  listArr(slot).splice(idx, 1)
  touch()
}
function setListItem(slot, idx, v) {
  listArr(slot)[idx] = v
  touch()
}

const touch = () => {
  if (props.slot) {
    const v = props.modelValue
    emit('update:modelValue', Array.isArray(v) ? v.slice() : typeof v === 'object' && v !== null ? { ...v } : v)
  } else value.value = { ...value.value }
}

// map 的「值」槽：去掉 is_map_key，按值类型渲染（避免递归又出现键输入框）
const valueSlotOf = (slot) => ({ ...slot, isMapKey: 0 })
</script>

<template>
  <div class="value-tree-form">
    <div v-for="slot in renderSlots" :key="slot.keyName" class="vfield">
      <div class="vfield-label">
        <span class="vfield-name">{{ slot.label || slot.keyName }}</span>
        <el-tag v-if="isMap(slot)" size="small" type="warning">map</el-tag>
        <el-tag v-else-if="vt(slot).kind === 'LIST'" size="small" type="success">list</el-tag>
        <el-tag v-else-if="vt(slot).kind === 'OBJECT'" size="small" type="info">object</el-tag>
        <span v-if="slot.required" class="vfield-req">*</span>
        <span v-if="slot.sensitive" class="vfield-sec">敏感</span>
      </div>

      <!-- map：键 + 值（值类型任意） -->
      <template v-if="isMap(slot)">
        <div v-if="!mapKeys(slot).length" class="vfield-empty">（无键值，点击下方添加）</div>
        <div v-for="(k, idx) in mapKeys(slot)" :key="idx" class="vfield-row">
          <el-input
            class="vfield-mapkey"
            :model-value="k"
            :placeholder="slot.mapKeyValidator ? `键（正则: ${slot.mapKeyValidator}）` : '键，如 /**、/api/**'"
            size="small"
            @input="(v) => renameMapKey(slot, idx, v)"
          />
          <div class="vfield-mapval">
            <ValueTreeForm
              :model-value="mapValAt(slot, idx)"
              :slot="valueSlotOf(slot)"
              @update:model-value="(v) => setMapVal(slot, idx, v)"
            />
          </div>
          <el-button size="small" type="danger" text @click="removeMapKey(slot, idx)">删除</el-button>
        </div>
        <el-button size="small" @click="addMapKey(slot)">+ 添加键值</el-button>
      </template>

      <!-- list：动态行 -->
      <template v-else-if="vt(slot).kind === 'LIST'">
        <div v-if="!listArr(slot).length" class="vfield-empty">（空列表，点击下方添加）</div>
        <div v-for="(item, idx) in listArr(slot)" :key="idx" class="vfield-row">
          <div class="vfield-item">
            <ValueTreeForm
              :model-value="itemAt(slot, idx)"
              :slot="itemOf(vt(slot))"
              @update:model-value="(v) => setListItem(slot, idx, v)"
            />
          </div>
          <el-button size="small" type="danger" text @click="removeListItem(slot, idx)">删除</el-button>
        </div>
        <el-button size="small" @click="addListItem(slot)">+ 添加元素</el-button>
      </template>

      <!-- object：嵌套字段 -->
      <template v-else-if="vt(slot).kind === 'OBJECT'">
        <div class="vfield-object">
          <ValueTreeForm
            :model-value="slotValue(slot)"
            :slots="fieldsOf(vt(slot))"
            @update:model-value="(v) => setSlotValue(slot, v)"
          />
        </div>
      </template>

      <!-- BASIC -->
      <template v-else>
        <el-switch
          v-if="vt(slot).name === 'BOOL'"
          :model-value="slotValue(slot)"
          @update:model-value="(v) => setSlotValue(slot, v)"
          @change="touch"
        />
        <el-input-number
          v-else-if="['INT', 'LONG', 'DOUBLE'].includes(vt(slot).name)"
          :model-value="slotValue(slot)"
          :precision="vt(slot).name === 'DOUBLE' ? 2 : 0"
          :step="vt(slot).name === 'DOUBLE' ? 0.5 : 1"
          :controls="false"
          class="w-full"
          @update:model-value="(v) => setSlotValue(slot, v)"
        />
        <el-select
          v-else-if="vt(slot).name === 'ENUM'"
          :model-value="slotValue(slot)"
          filterable
          clearable
          class="w-full"
          :placeholder="slot.placeholder || '选择取值'"
          @update:model-value="(v) => setSlotValue(slot, v)"
        >
          <el-option v-for="opt in parseOptions(slot)" :key="opt" :label="opt" :value="opt" />
        </el-select>
        <el-input
          v-else-if="vt(slot).name === 'SENSITIVE'"
          :model-value="slotValue(slot)"
          type="password"
          show-password
          :placeholder="slot.placeholder || '输入敏感值'"
          @update:model-value="(v) => setSlotValue(slot, v)"
        />
        <el-input
          v-else-if="vt(slot).name === 'TEXTAREA'"
          :model-value="slotValue(slot)"
          type="textarea"
          :rows="3"
          :placeholder="slot.placeholder || ''"
          @update:model-value="(v) => setSlotValue(slot, v)"
        />
        <el-input
          v-else
          :model-value="slotValue(slot)"
          :placeholder="slot.placeholder || (vt(slot).name === 'DURATION' ? '如 500ms / 30s' : vt(slot).name === 'SIZE' ? '如 5MB / 16000B' : '')"
          @update:model-value="(v) => setSlotValue(slot, v)"
        />
        <div v-if="slot.validator" class="vfield-hint">校验：{{ slot.validator }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.value-tree-form {
  width: 100%;
}
.vfield {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fafbfc;
}
.vfield-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #303133;
}
.vfield-name {
  font-weight: 600;
}
.vfield-req {
  color: #f56c6c;
  margin-left: 2px;
}
.vfield-sec {
  color: #e6a23c;
  font-size: 12px;
}
.vfield-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.vfield-mapkey {
  width: 220px;
  flex-shrink: 0;
}
.vfield-mapval,
.vfield-item {
  flex: 1;
  min-width: 0;
}
.vfield-object {
  padding: 4px 0 0 8px;
}
.vfield-empty {
  color: #909399;
  font-size: 12px;
  margin-bottom: 6px;
}
.vfield-hint {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
