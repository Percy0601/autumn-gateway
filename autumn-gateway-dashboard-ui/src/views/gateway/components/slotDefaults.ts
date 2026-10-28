// 模板槽默认值：根据递归 valueType 结构生成实例初始值（与 template/index.vue 的 buildDefault 同逻辑）
export function buildSlotDefaults(slots: any[]): Record<string, any> {
  const obj: Record<string, any> = {}
  for (const slot of slots || []) {
    obj[slot.keyName] = defaultOf(slot)
  }
  return obj
}

export function defaultOf(slot: any): any {
  const t = slot?.valueType || {}
  if (slot.isMapKey === 1) return {}
  if (t.kind === 'LIST') return []
  if (t.kind === 'OBJECT') {
    const o: Record<string, any> = {}
    ;(t.fields || [])
      .filter((f: any) => f.role === 'FIELD' || !f.role)
      .forEach((f: any) => {
        o[f.keyName] = defaultOf(f)
      })
    return o
  }
  const dv = slot.defaultValue
  if (dv !== undefined && dv !== null && dv !== '') {
    if (['INT', 'LONG', 'DOUBLE'].includes(t.name)) return Number(dv)
    if (t.name === 'BOOL') return dv === true || dv === 'true'
    return String(dv)
  }
  if (t.name === 'BOOL') return false
  if (['INT', 'LONG', 'DOUBLE'].includes(t.name)) return undefined
  return ''
}
