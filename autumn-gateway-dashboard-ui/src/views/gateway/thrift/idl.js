/**
 * 轻量 Thrift IDL 解析器（仅用于 Dashboard 表单联动与预检，不参与网关运行时）。
 *
 * 目的：让「服务编码 / 方法名 / 参数名」不再靠人肉抄 IDL。
 * 覆盖范围与 autumn-protocol-extension 的 IdlParser 子集保持一致：
 *   - 支持 struct / enum / exception / union、service、oneway、throws、容器类型；
 *   - 跳过 namespace / include / typedef / const / senum，不支持 service extends；
 *     遇到这些语法会给出 warning，提醒用户先拍平 IDL。
 *
 * 这里是"辅助提示"而非权威校验：真正的解析在网关启动期完成。
 */

function stripComments(text) {
  return String(text || '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '')
    .replace(/^\s*#[^\n]*/gm, '')
}

/** 从 openIdx（指向 '{'）开始做括号配对，返回块内文本 */
function readBlock(text, openIdx) {
  let depth = 0
  for (let i = openIdx; i < text.length; i++) {
    if (text[i] === '{') depth++
    else if (text[i] === '}') {
      depth--
      if (depth === 0) return text.slice(openIdx + 1, i)
    }
  }
  return text.slice(openIdx + 1)
}

/** 按顶层分隔符切参数，兼容 map<string, list<i32>> 里的逗号与 thrift 允许的分号 */
function splitParams(text) {
  const parts = []
  let depth = 0
  let buf = ''
  for (const ch of text) {
    if (ch === '<' || ch === '(') depth++
    else if (ch === '>' || ch === ')') depth--
    if ((ch === ',' || ch === ';') && depth === 0) {
      if (buf.trim()) parts.push(buf.trim())
      buf = ''
    } else {
      buf += ch
    }
  }
  if (buf.trim()) parts.push(buf.trim())
  return parts
}

function parseParams(text, warnings, methodName) {
  return splitParams(text)
    .map((raw) => {
      const m = raw.match(/^(\d+)\s*:\s*(?:(required|optional)\s+)?(.+?)\s+([A-Za-z_]\w*)\s*(?:=\s*.*)?$/s)
      if (!m) {
        warnings.push(`方法 ${methodName} 的参数「${raw}」未能识别，请检查写法（应为 1: required string name）`)
        return null
      }
      return {
        id: Number(m[1]),
        required: m[2] === 'required',
        optional: m[2] === 'optional',
        type: m[3].trim(),
        name: m[4],
      }
    })
    .filter(Boolean)
}

function unsupportedWarnings(text) {
  const warnings = []
  const rules = [
    [/^\s*include\s+/m, 'IDL 里的 include 不会被网关解析，请把被引用的定义合并到同一份文本'],
    [/^\s*typedef\s+/m, 'IDL 里的 typedef 不会被网关解析，请展开成原始类型'],
    [/^\s*const\s+/m, 'IDL 里的 const 不会被网关解析，请改成字面量'],
    [/^\s*senum\s+/m, 'IDL 里的 senum 不会被网关解析，请改成 enum 或 string'],
    [/\bservice\s+\w+\s+extends\s+\w+/m, '网关解析器不支持 service extends，请把父服务方法抄进当前 service'],
  ]
  for (const [re, msg] of rules) if (re.test(text)) warnings.push(msg)
  return warnings
}

/**
 * 解析 IDL 文本。
 * @returns {{ ok: boolean, services: Array<{name, methods: Array}>, warnings: string[], empty: boolean }}
 *          service.methods[] = { name, returnType, oneway, params: [{id,name,type,required,optional}] }
 */
export function parseIdl(text) {
  const source = stripComments(text)
  const warnings = unsupportedWarnings(source)
  if (!source.trim()) return { ok: true, services: [], warnings, empty: true }

  const services = []
  const serviceRe = /\bservice\s+([A-Za-z_]\w*)\s*(?:extends\s+([A-Za-z_]\w*)\s*)?\{/g
  let match
  while ((match = serviceRe.exec(source)) !== null) {
    const name = match[1]
    const body = readBlock(source, source.indexOf('{', match.index))
    const methods = []
    const methodRe = /(oneway\s+)?\b([A-Za-z_]\w*(?:\s*<[^<>]*(?:<[^<>]*>[^<>]*)*>)*)\s+([A-Za-z_]\w*)\s*\(([^)]*)\)/g
    let mm
    while ((mm = methodRe.exec(body)) !== null) {
      methods.push({
        name: mm[3],
        returnType: mm[2].replace(/\s+/g, ''),
        oneway: !!mm[1],
        params: parseParams(mm[4], warnings, mm[3]),
      })
    }
    if (!methods.length) warnings.push(`service ${name} 里没解析到方法，请检查 IDL 写法`)
    services.push({ name, methods })
  }

  if (!services.length && source.trim()) {
    warnings.push('没有解析到 service 定义；服务编码必须等于 IDL 里的 service 名，网关启动期会做严格匹配')
  }
  return { ok: services.length > 0, services, warnings, empty: false }
}

/** IDL 里的 service 名列表 */
export function idlServiceNames(text) {
  return parseIdl(text).services.map((s) => s.name)
}

/**
 * 校验「服务编码」与内联 IDL 的一致性。
 * 只有填了内联 IDL 才能判断；只填 idl-location 时返回 unchecked。
 */
export function checkServiceCode(text, serviceCode) {
  const parsed = parseIdl(text)
  if (parsed.empty) return { state: 'unchecked', names: [], message: '' }
  if (!parsed.services.length) {
    return { state: 'unknown', names: [], message: '内联 IDL 里没有解析到 service 定义，无法核对服务编码' }
  }
  const names = parsed.services.map((s) => s.name)
  if (!serviceCode) return { state: 'mismatch', names, message: `服务编码建议填 ${names[0]}` }
  if (names.includes(serviceCode)) return { state: 'ok', names, message: '' }
  return {
    state: 'mismatch',
    names,
    message: `服务编码「${serviceCode}」与 IDL 里的 service 名（${names.join('、')}）不一致，网关启动会失败`,
  }
}

/** 取某个 service 的方法列表（用于方法下拉 / 参数一键生成） */
export function methodsOf(text, serviceName) {
  const parsed = parseIdl(text)
  const svc = parsed.services.find((s) => s.name === serviceName) || parsed.services[0]
  return svc ? svc.methods : []
}

/** 依据 IDL 参数类型给 curl 示例用的样例值 */
export function sampleValueForType(type) {
  const t = String(type || '').replace(/\s+/g, '').toLowerCase()
  if (!t) return 'demo'
  if (t.startsWith('bool')) return true
  if (/^(byte|i8|i16|i32|i64|int|double)$/.test(t) || t === 'enum') return 1
  if (t.startsWith('list<') || t.startsWith('set<')) return [1, 2]
  if (t.startsWith('map<')) return { k: 1 }
  if (t === 'binary') return 'ZGVtbw=='
  if (t === 'string') return 'demo'
  return {}
}
