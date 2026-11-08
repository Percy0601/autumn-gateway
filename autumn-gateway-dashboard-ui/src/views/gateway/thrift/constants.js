/**
 * 协议转换（HTTP → Thrift 泛化调用）前端常量与默认模型。
 *
 * 枚举与网关侧 ThriftGenericProperties.ArgumentSource / ResponseMode 对齐；
 * 字段语义与 dashboard 侧 GwThriftConfigService.exportLines() 的导出键对齐。
 * 改这里之前先确认后端枚举，避免页面选项和网关实际行为漂移。
 */

export const ARGUMENT_SOURCES = [
  { value: 'BODY', label: 'BODY · 请求体', keyHint: '请求体 JSON 的字段名', keyExample: 'userName', keyDisabled: false, valueDisabled: true },
  { value: 'QUERY', label: 'QUERY · 查询参数', keyHint: 'queryString 参数名', keyExample: 'userName', keyDisabled: false, valueDisabled: true },
  { value: 'HEADER', label: 'HEADER · 请求头', keyHint: 'HTTP 头名，大小写不敏感', keyExample: 'X-Tenant', keyDisabled: false, valueDisabled: true },
  { value: 'PATH', label: 'PATH · 路径变量', keyHint: '路由 Path 里的 {变量} 名', keyExample: 'id', keyDisabled: false, valueDisabled: true },
  { value: 'FIXED', label: 'FIXED · 常量', keyHint: '常量不需要取值 key', keyExample: '', keyDisabled: true, valueDisabled: false },
]

export const SOURCE_META = Object.fromEntries(ARGUMENT_SOURCES.map((s) => [s.value, s]))

export const RESPONSE_MODES = [
  { value: 'ENVELOPE', label: 'ENVELOPE · 完整信封', desc: '返回 { success, costMillis, data } 或 { success, costMillis, errorType, message }' },
  { value: 'DATA', label: 'DATA · 只要结果', desc: '只返回 data 本身，适合前端直接消费' },
]

export const DEFAULT_SERVICE_FORM = () => ({
  serviceCode: '',
  serviceName: '',
  host: '127.0.0.1',
  port: 19090,
  idlLocation: '',
  idlContent: '',
  multiplexedName: '',
  framed: 1,
  multiplexed: 0,
  status: 1,
  remark: '',
})

export const DEFAULT_CONVERSION_FORM = () => ({
  conversionCode: '',
  conversionName: '',
  serviceCode: '',
  methodName: '',
  httpPath: '',
  includeBody: 1,
  responseMode: 'ENVELOPE',
  successStatus: 0,
  status: 1,
  remark: '',
  argumentsJson: '',
})

export const newArgRule = (preset = {}) => ({
  arg: '',
  source: 'BODY',
  key: '',
  value: '',
  required: false,
  defaultValue: '',
  ...preset,
})

/** 弹窗里可直接插入的示例 IDL（与网关 IdlParser 支持的子集一致） */
export const IDL_TEMPLATES = [
  {
    label: '最简 DemoService（推荐先跑通）',
    value: `service DemoService {
  string echo(1: string msg),
  i32 add(1: required i32 a, 2: required i32 b)
}`,
  },
  {
    label: 'autumn-service-framework · SomeService',
    value: `namespace java xyz.wewin.autumn.sample.api

struct User {
  1: required i32 userId,
  2: required string username,
  3: optional map<string, string> desc
}

service SomeService {
  string echo(1: string msg),
  i32 addUser(1: required User user),
  list<User> findUserByIds(1: list<i32> idList)
}`,
  },
  {
    label: 'protocol-extension 测试 IDL · HelloWorldService',
    value: `enum Status { OK = 1, FAIL = 2 }
struct Model {
  1: required i32 id,
  2: optional string name = "anon",
  3: list<i32> scores
}
exception ErrorEx { 1: string message }
service HelloWorldService {
  string sayHello(1: required string username),
  Model returnModel(1: required string name),
  i32 sum(1: required list<i32> nums),
  map<string,i32> echoMap(1: required map<string,i32> data),
  void fail(1: required string reason) throws (1: ErrorEx e),
  oneway void ping(1: required string msg)
}`,
  },
]

/** 配置闭环引导步骤。done 由页面按当前数据计算，不写死。 */
export const GUIDE_STEPS = [
  {
    key: 'service',
    title: '登记 Thrift 服务',
    desc: '填服务端点 + IDL；服务编码必须与 IDL 里的 service 名一致，否则网关启动期会直接失败。',
    action: '新增服务',
  },
  {
    key: 'mapping',
    title: '编写转换规则',
    desc: '定义 HTTP 的 body/query/header/path/常量如何映射到 thrift 参数，以及响应形态。',
    action: '新增规则',
  },
  {
    key: 'route',
    title: '在路由上挂 ThriftGeneric',
    desc: '规则表里的「服务 / 方法 / HTTP 路径」只是标注，不参与导出。真正调用哪个服务与方法，由路由过滤器的短写决定。',
    action: '去网关路由',
  },
  {
    key: 'publish',
    title: '下发配置并重启网关',
    desc: '到「配置同步」预览 / 下载 / 推送到 Consul、Nacos。IDL 服务目录在启动期加载，改完需重启网关才生效。',
    action: '去配置同步',
  },
]
