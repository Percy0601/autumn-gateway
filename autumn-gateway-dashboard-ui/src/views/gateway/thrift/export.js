/**
 * 与 dashboard 的 GwThriftConfigService.exportLines() 保持一致的"前端同款"导出，
 * 以及可直接拿去联调的路由 / curl 片段。
 *
 * 这里只做展示与复制，不落库、不改后端：页面保存后点「导出预览」看到的仍是后端生成的权威文本。
 * 实时预览的价值是：编辑表单时就能看到这条规则会变成什么，不用先保存再猜。
 */
import { methodsOf, sampleValueForType } from './idl'

const hasText = (v) => v !== null && v !== undefined && String(v).trim() !== ''
const inlineIdl = (s) => String(s || '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\n/g, '\\n')

/** 单个服务 → autumn.thrift.services[n].* */
export function serviceToProperties(service, index = 0) {
  const base = `autumn.thrift.services[${index}]`
  const lines = [
    `${base}.name=${service.serviceCode || ''}`,
    `${base}.host=${service.host || ''}`,
    `${base}.port=${service.port || 0}`,
  ]
  if (hasText(service.idlLocation)) lines.push(`${base}.idl-location=${String(service.idlLocation).trim()}`)
  if (hasText(service.idlContent)) lines.push(`${base}.idl=${inlineIdl(service.idlContent)}`)
  lines.push(`${base}.framed=${service.framed == null || service.framed === 1}`)
  lines.push(`${base}.multiplexed=${service.multiplexed === 1}`)
  if (service.multiplexed === 1 && hasText(service.multiplexedName)) {
    lines.push(`${base}.multiplexed-name=${service.multiplexedName}`)
  }
  return lines
}

/** 单条规则 → autumn.thrift.mappings[n].*（rules 为表单里的参数行数组） */
export function mappingToProperties(form, rules, index = 0) {
  const base = `autumn.thrift.mappings[${index}]`
  const lines = [
    `${base}.name=${form.conversionCode || ''}`,
    `${base}.include-body=${form.includeBody == null || form.includeBody === 1}`,
  ]
  ;(rules || []).forEach((r, a) => {
    const ab = `${base}.arguments[${a}]`
    lines.push(`${ab}.arg=${r.arg || ''}`)
    lines.push(`${ab}.source=${r.source || 'BODY'}`)
    if (hasText(r.key)) lines.push(`${ab}.key=${r.key}`)
    if (hasText(r.value) && String(r.source).toUpperCase() === 'FIXED') lines.push(`${ab}.value=${r.value}`)
    if (r.required) lines.push(`${ab}.required=true`)
    if (hasText(r.defaultValue)) lines.push(`${ab}.default-value=${r.defaultValue}`)
  })
  lines.push(`${base}.response.mode=${String(form.responseMode || 'ENVELOPE').toUpperCase()}`)
  if (Number(form.successStatus) > 0) lines.push(`${base}.response.success-status=${Number(form.successStatus)}`)
  return lines
}

/** 编辑弹窗里的完整预览：服务块 + 这条规则块 */
export function buildRulePreview(form, rules, services) {
  const svc = (services || []).find((s) => s.serviceCode === form.serviceCode)
  const head = ['# 本条规则的实时预览（未保存，仅用于核对）', '# 完整配置请在页面顶部点「导出预览」查看后端生成结果']
  const serviceLines = svc ? ['', ...serviceToProperties(svc, 0)] : ['', '# 尚未选择已登记的服务，服务块由「导出预览」统一生成']
  return [...head, ...serviceLines, '', ...mappingToProperties(form, rules, 0)].join('\n')
}

/** 路由过滤器短写；方法留空表示动态分发 */
export function thriftGenericValue(form) {
  const service = form.serviceCode || ''
  const method = form.methodName || ''
  const mapping = form.conversionCode || ''
  return [service, method, mapping].join(',')
}

function parseHttpPath(httpPath, form) {
  const raw = String(httpPath || '').trim()
  const m = raw.match(/^(?:(GET|POST|PUT|DELETE|PATCH)\s+)?(\/\S*)/i)
  const verb = (m && m[1] ? m[1] : 'POST').toUpperCase()
  const path = m && m[2] ? m[2] : `/thrift/${form.serviceCode || '{service}'}/${form.methodName || '{method}'}`
  return { verb, path }
}

/** 生成联调片段：YAML 路由 / 过滤器短写 / curl */
export function buildSnippets(form, rules, services, gatewayBase = 'http://localhost:8080') {
  const { verb, path } = parseHttpPath(form.httpPath, form)
  const svc = (services || []).find((s) => s.serviceCode === form.serviceCode)
  const methodSpec = svc?.idlContent
    ? methodsOf(svc.idlContent, form.serviceCode).find((m) => m.name === form.methodName)
    : null
  const typeOfArg = (arg) => methodSpec?.params?.find((p) => p.name === arg)?.type || 'string'

  const yaml = [
    `- id: thrift-${form.conversionCode || 'demo'}`,
    `  uri: thrift://${form.serviceCode || 'demo-service'}   # 占位，ThriftGeneric 会短路转发`,
    '  predicates:',
    `    - Path=${path}`,
    '  filters:',
    `    - ThriftGeneric=${thriftGenericValue(form)}`,
  ].join('\n')

  // ---- curl：按参数来源把示例值分别放进 body / query / header / path ----
  const body = {}
  const query = []
  const headers = []
  let finalPath = path
  const list = rules || []
  list.forEach((r) => {
    if (!r.arg) return
    const sample = sampleValueForType(typeOfArg(r.arg))
    const key = r.key || r.arg
    const src = String(r.source || 'BODY').toUpperCase()
    const text = typeof sample === 'object' ? JSON.stringify(sample) : String(sample)
    if (src === 'BODY') body[key] = sample
    else if (src === 'QUERY') query.push(`${encodeURIComponent(key)}=${encodeURIComponent(text)}`)
    else if (src === 'HEADER') headers.push(`-H '${key}: ${text}'`)
    else if (src === 'PATH') finalPath = finalPath.replace(`{${key}}`, encodeURIComponent(text))
    // FIXED 由网关注入，客户端不用传
  })

  // include-body=1 且没有 BODY 规则时，请求体直接按 IDL 的参数名打底；
  // 被其它来源规则覆盖的参数不会读请求体，示例里就不带它，避免误导
  if ((form.includeBody == null || form.includeBody === 1) && !Object.keys(body).length && methodSpec?.params?.length) {
    const overridden = new Set(list.filter((r) => r.arg && String(r.source).toUpperCase() !== 'BODY').map((r) => r.arg))
    methodSpec.params.forEach((p) => {
      if (!overridden.has(p.name)) body[p.name] = sampleValueForType(p.type)
    })
  }
  // include-body=0 且没有 BODY 规则时 body 为空，curl 自然不会带 -d

  const url = `${String(gatewayBase || '').replace(/\/+$/, '')}${finalPath}${query.length ? `?${query.join('&')}` : ''}`
  const cmd = [`curl -X ${verb} '${url}'`, `-H 'Content-Type: application/json'`]
  headers.forEach((h) => cmd.push(h))
  if (Object.keys(body).length) cmd.push(`-d '${JSON.stringify(body)}'`)
  const curl = cmd.join(' \\\n  ')

  return { yaml, filter: thriftGenericValue(form), curl, path: finalPath }
}
